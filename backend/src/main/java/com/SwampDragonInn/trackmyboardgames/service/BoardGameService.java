package com.SwampDragonInn.trackmyboardgames.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.SwampDragonInn.trackmyboardgames.api.BoardGameRequest;
import com.SwampDragonInn.trackmyboardgames.api.BoardGameResponse;
import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;
import com.SwampDragonInn.trackmyboardgames.domain.OutboxEvent;
import com.SwampDragonInn.trackmyboardgames.event.GameSnapshot;
import com.SwampDragonInn.trackmyboardgames.repository.BoardGameRepository;
import com.SwampDragonInn.trackmyboardgames.repository.GamePlayRepository;
import com.SwampDragonInn.trackmyboardgames.repository.OutboxEventRepository;
import com.SwampDragonInn.trackmyboardgames.repository.GamePlayRepository.PlayStats;

import tools.jackson.databind.json.JsonMapper;

// Applies game rules and ensures every database operation is limited to the signed-in user's profile.
@Service
public class BoardGameService
{

	private final BoardGameRepository boardGameRepository;
	private final GamePlayRepository gamePlayRepository;
	private final UserProfileService userProfileService;
	private final ImageUploadChecker imageUploadChecker;
	private final OutboxEventRepository outboxEventRepository;
	private final JsonMapper jsonMapper;

	public BoardGameService(BoardGameRepository boardGameRepository, GamePlayRepository gamePlayRepository,
			UserProfileService userProfileService, ImageUploadChecker imageUploadChecker,
			OutboxEventRepository outboxEventRepository, JsonMapper jsonMapper)
	{
		this.imageUploadChecker = imageUploadChecker;
		this.boardGameRepository = boardGameRepository;
		this.gamePlayRepository = gamePlayRepository;
		this.userProfileService = userProfileService;
		this.outboxEventRepository = outboxEventRepository;
		this.jsonMapper = jsonMapper;
	}

	// List the owner's games alphabetically, optionally filtering by name, each with its play totals.
	@Transactional
	public List<BoardGameResponse> findAll(String subjectIssuer, String subject, String search)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);

		List<BoardGame> games = findGamesByOwner(owner.getId(), search);
		Map<UUID, PlayStats> statsByGameId = loadStatsByGameId(owner.getId());

		List<BoardGameResponse> responses = new ArrayList<>();
		for (BoardGame game : games)
		{
			// A game with no plays has no entry in the map, so this is null and shows as zero plays.
			PlayStats stats = statsByGameId.get(game.getId());
			responses.add(toResponse(game, stats));
		}

		return responses;
	}

	// Return one game only when it belongs to the authenticated user.
	@Transactional
	public BoardGameResponse findById(String subjectIssuer, String subject, UUID gameId)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);

		return toResponse(findOwnedGame(owner.getId(), gameId), loadStats(gameId, owner.getId()));
	}

	// Validate the submitted details and save the new game under the authenticated user.
	@Transactional
	public BoardGameResponse create(String subjectIssuer, String subject, BoardGameRequest request)
	{
		validatePlayerRange(request);
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		String name = request.name().trim();
		String edition = cleanEdition(request.edition());
		if (this.boardGameRepository.existsByOwner_IdAndNameIgnoreCaseAndEditionIgnoreCase(owner.getId(), name, edition))
		{
			throw duplicateGame();
		}
		BoardGame game = new BoardGame(owner, name, edition, request.minPlayers(),
				request.maxPlayers(), request.bestPlayers(), request.playTimeMinutes(), request.notes());

		applyCover(game, request.coverImage());
		BoardGame savedGame = saveGame(game);
		saveGameEvent("GameAdded", owner.getId(), savedGame, savedGame.getCreatedAt());

		return toResponse(savedGame, null);
	}

	// Validate and replace the details of an existing game owned by this user.
	@Transactional
	public BoardGameResponse update(String subjectIssuer, String subject, UUID gameId, BoardGameRequest request)
	{
		validatePlayerRange(request);
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		BoardGame game = findOwnedGame(owner.getId(), gameId);
		String name = request.name().trim();
		String edition = cleanEdition(request.edition());
		if (this.boardGameRepository.existsByOwner_IdAndNameIgnoreCaseAndEditionIgnoreCaseAndIdNot(owner.getId(), name, edition, gameId))
		{
			throw duplicateGame();
		}
		game.updateDetails(name, edition, request.minPlayers(), request.maxPlayers(),
				request.bestPlayers(), request.playTimeMinutes(), request.notes());
		applyCover(game, request.coverImage());
		BoardGame savedGame = saveGame(game);
		saveGameEvent("GameUpdated", owner.getId(), savedGame, Instant.now());

		return toResponse(savedGame, loadStats(gameId, owner.getId()));
	}

	// Delete the requested game only if it belongs to the authenticated user.
	@Transactional
	public void delete(String subjectIssuer, String subject, UUID gameId)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		BoardGame game = findOwnedGame(owner.getId(), gameId);
		Instant deletedAt = Instant.now();
		this.boardGameRepository.delete(game);
		saveGameEvent("GameDeleted", owner.getId(), game, deletedAt);
	}

	// Keep the game snapshot even for deletion, after the source row has been removed.
	private void saveGameEvent(String eventType, UUID ownerId, BoardGame game, Instant occurredAt)
	{
		GameSnapshot payload = new GameSnapshot(game.getId(), game.getName(), game.getEdition());
		OutboxEvent event = new OutboxEvent(
				eventType,
				1,
				ownerId,
				game.getId(),
				this.jsonMapper.writeValueAsString(payload),
				occurredAt);

		// The event and game change commit or roll back together.
		this.outboxEventRepository.save(event);
	}

	// Get the owner's games in name order ; a blank search means no filtering.
	private List<BoardGame> findGamesByOwner(UUID ownerId, String search)
	{
		boolean hasSearch = search != null && !search.isBlank();

		if (hasSearch)
		{
			return this.boardGameRepository.findAllByOwner_IdAndNameContainingIgnoreCaseOrderByNameAsc(ownerId, search.trim());
		}

		return this.boardGameRepository.findAllByOwner_IdOrderByNameAsc(ownerId);
	}

	// Load play totals for all of the owner's games in one query and index them by game id for quick lookup.
	private Map<UUID, PlayStats> loadStatsByGameId(UUID ownerId)
	{
		Map<UUID, PlayStats> statsByGameId = new HashMap<>();

		for (PlayStats stats : this.gamePlayRepository.findPlayStatsByOwner(ownerId))
		{
			statsByGameId.put(stats.getGameId(), stats);
		}

		return statsByGameId;
	}

	// Look up one game's play totals; null means the game has never been played.
	private PlayStats loadStats(UUID gameId, UUID ownerId)
	{
		return this.gamePlayRepository.findPlayStatsByGame(gameId, ownerId).orElse(null);
	}

	// Build the response, treating missing stats as zero plays.
	private BoardGameResponse toResponse(BoardGame game, PlayStats stats)
	{
		if (stats == null)
		{
			return BoardGameResponse.from(game, 0, null);
		}

		return BoardGameResponse.from(game, stats.getPlayCount(), stats.getLastPlayedOn());
	}


	// Save the submitted cover photo, or remove the cover when none was sent.
	private void applyCover(BoardGame game, String coverImage)
	{
		if (coverImage == null || coverImage.isBlank())
		{
			game.removeCover();
			return;
		}

		StoredImage image = this.imageUploadChecker.checkDataUrl(coverImage);
		game.setCover(image.contentType(), image.data());
	}

	// Treat another user's game the same as a missing game so its existence is not exposed.
	private BoardGame findOwnedGame(UUID ownerId, UUID gameId)
	{
		return this.boardGameRepository.findByIdAndOwner_Id(gameId, ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	// An edition is optional: missing or blank becomes empty text so "no edition" is stored the same way every time.
	private String cleanEdition(String edition)
	{
		return edition == null ? "" : edition.trim();
	}

	// Save right away so the database's unique index can reject a duplicate that slipped past the earlier check
	// (for example two quick clicks); it is reported with the same friendly 409 error.
	private BoardGame saveGame(BoardGame game)
	{
		try
		{
			return this.boardGameRepository.saveAndFlush(game);
		}
		catch (DataIntegrityViolationException exception)
		{
			throw duplicateGame();
		}
	}

	// The 409 Conflict error shown when the user already owns this name and edition.
	private ResponseStatusException duplicateGame()
	{
		return new ResponseStatusException(HttpStatus.CONFLICT,
				"You already have a game with this name and edition. If this is a different version, add it using the Edition field.");
	}

	// Check the relationship between optional minimum and maximum player counts.
	private void validatePlayerRange(BoardGameRequest request)
	{
		if (request.minPlayers() != null && request.maxPlayers() != null
				&& request.maxPlayers() < request.minPlayers())
		{
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Maximum players must be at least the minimum players.");
		}
	}
}
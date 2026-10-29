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
import com.SwampDragonInn.trackmyboardgames.domain.GameCollection;
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
	private final GameCollectionService gameCollectionService;

	/**
	 * Supplies the helpers used to save games, read play totals, and record game-change events.
	 *
	 * @param boardGameRepository reads and saves games in the database
	 * @param gamePlayRepository reads recorded plays and their totals
	 * @param userProfileService finds or creates the signed-in user's local account
	 * @param imageUploadChecker checks that a submitted cover is an allowed image
	 * @param outboxEventRepository saves game-change events for later delivery
	 * @param jsonMapper converts event details into JSON text
	 * @param gameCollectionService checks that the requested collection belongs to the user
	 */
	public BoardGameService(BoardGameRepository boardGameRepository, GamePlayRepository gamePlayRepository,
			UserProfileService userProfileService, ImageUploadChecker imageUploadChecker,
			OutboxEventRepository outboxEventRepository, JsonMapper jsonMapper, GameCollectionService gameCollectionService)
	{
		this.imageUploadChecker = imageUploadChecker;
		this.boardGameRepository = boardGameRepository;
		this.gamePlayRepository = gamePlayRepository;
		this.userProfileService = userProfileService;
		this.outboxEventRepository = outboxEventRepository;
		this.jsonMapper = jsonMapper;
		this.gameCollectionService = gameCollectionService;
	}

	/**
	 * Lists the signed-in user's games in name order, including each game's play totals.
	 *
	 * @param subjectIssuer identifies the login provider
	 * @param subject identifies the signed-in user within that provider
	 * @param search limits results to names containing this text; null or blank includes all games
	 * @return the matching games and their play totals for the collection page
	 */
	@Transactional
	public List<BoardGameResponse> findAll(String subjectIssuer, String subject, String search)
	{
		return findAll(subjectIssuer, subject, search, null);
	}

	/**
	 * Reads games from the chosen owned collection. Older callers without a collection ID still read all their games.
	 */
	@Transactional
	public List<BoardGameResponse> findAll(String subjectIssuer, String subject, String search, UUID collectionId)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);

		List<BoardGame> games;
		if (collectionId == null)
		{
			games = findGamesByOwner(owner.getId(), search);
		}
		else
		{
			this.gameCollectionService.findOwnedCollection(collectionId, owner.getId());
			games = search != null && !search.isBlank()
					? this.boardGameRepository.findAllByOwner_IdAndCollection_IdAndNameContainingIgnoreCaseOrderByNameAsc(
							owner.getId(), collectionId, search.trim())
					: this.boardGameRepository.findAllByOwner_IdAndCollection_IdOrderByNameAsc(owner.getId(), collectionId);
		}
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

	/**
	 * Gets one game from the signed-in user's collection.
	 *
	 * @param subjectIssuer identifies the login provider
	 * @param subject identifies the signed-in user within that provider
	 * @param gameId identifies the game to look up
	 * @return the game details and its play totals
	 * @throws ResponseStatusException if the game is missing or belongs to someone else
	 */
	@Transactional
	public BoardGameResponse findById(String subjectIssuer, String subject, UUID gameId)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);

		return toResponse(findOwnedGame(owner.getId(), gameId), loadStats(gameId, owner.getId()));
	}

	/**
	 * Saves a new game in the signed-in user's collection and records its game-added event.
	 *
	 * @param subjectIssuer identifies the login provider
	 * @param subject identifies the signed-in user within that provider
	 * @param request contains the new board game's details entered in the form
	 * @return the saved game details sent back to the page
	 */
	@Transactional
	public BoardGameResponse create(String subjectIssuer, String subject, BoardGameRequest request)
	{
		return create(subjectIssuer, subject, request, null);
	}

	/**
	 * Saves a game in the collection being viewed, or in the saved default for older callers without a collection ID.
	 */
	@Transactional
	public BoardGameResponse create(String subjectIssuer, String subject, BoardGameRequest request, UUID collectionId)
	{
		validatePlayerRange(request);
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		GameCollection collection = collectionId == null ? owner.getDefaultCollection()
				: this.gameCollectionService.findOwnedCollection(collectionId, owner.getId());
		String name = request.name().trim();
		String edition = cleanEdition(request.edition());

		if (this.boardGameRepository.existsByOwner_IdAndNameIgnoreCaseAndEditionIgnoreCase(owner.getId(), name, edition))
		{
			throw duplicateGame();
		}

		BoardGame game = new BoardGame(owner, collection, name, edition, request.minPlayers(),
				request.maxPlayers(), request.bestPlayers(), request.playTimeMinutes(), request.notes());

		applyCover(game, request.coverImage());
		BoardGame savedGame = saveGame(game);
		saveGameEvent("GameAdded", owner.getId(), savedGame, savedGame.getCreatedAt());

		return toResponse(savedGame, null);
	}

	/**
	 * Saves edits to a game in the signed-in user's collection and records its game-updated event.
	 *
	 * @param subjectIssuer identifies the login provider
	 * @param subject identifies the signed-in user within that provider
	 * @param gameId identifies the game being edited
	 * @param request contains the replacement game details entered in the form
	 * @return the updated game details and its existing play totals
	 */
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

	/**
	 * Deletes a game from the signed-in user's collection and records its game-deleted event.
	 *
	 * @param subjectIssuer identifies the login provider
	 * @param subject identifies the signed-in user within that provider
	 * @param gameId identifies the game to delete
	 */
	@Transactional
	public void delete(String subjectIssuer, String subject, UUID gameId)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		BoardGame game = findOwnedGame(owner.getId(), gameId);
		Instant deletedAt = Instant.now();
		this.boardGameRepository.delete(game);
		saveGameEvent("GameDeleted", owner.getId(), game, deletedAt);
	}

	/**
	 * Saves a note about a game change so the background sender can deliver it later.
	 * The note keeps the game's identifying details even if the game is being deleted.
	 *
	 * @param eventType names the change, such as GameAdded, GameUpdated, or GameDeleted
	 * @param ownerId identifies the user whose game changed
	 * @param game supplies the game's ID, name, and edition for the saved note
	 * @param occurredAt records when the change happened
	 */
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

		// Save both the event and game change commit or roll back together.
		this.outboxEventRepository.save(event);
	}

	/**
	 * Reads one user's games in name order, optionally narrowing the list by name.
	 *
	 * @param ownerId identifies the user whose collection to read
	 * @param search the name text to match; null or blank includes all games
	 * @return the matching saved games
	 */
	private List<BoardGame> findGamesByOwner(UUID ownerId, String search)
	{
		boolean hasSearch = search != null && !search.isBlank();

		if (hasSearch)
		{
			return this.boardGameRepository.findAllByOwner_IdAndNameContainingIgnoreCaseOrderByNameAsc(ownerId, search.trim());
		}

		return this.boardGameRepository.findAllByOwner_IdOrderByNameAsc(ownerId);
	}

	/**
	 * Reads a user's play totals together so they can be matched to games without a query per game.
	 *
	 * @param ownerId identifies the user whose play totals to read
	 * @return play totals grouped by game ID; games with no recorded plays have no entry
	 */
	private Map<UUID, PlayStats> loadStatsByGameId(UUID ownerId)
	{
		Map<UUID, PlayStats> statsByGameId = new HashMap<>();

		for (PlayStats stats : this.gamePlayRepository.findPlayStatsByOwner(ownerId))
		{
			statsByGameId.put(stats.getGameId(), stats);
		}

		return statsByGameId;
	}

	/**
	 * Reads the play count and latest played-on date for one game.
	 *
	 * @param gameId identifies the game whose plays to count
	 * @param ownerId identifies the user who owns the game
	 * @return the game's play totals, or null if there are no recorded plays
	 */
	private PlayStats loadStats(UUID gameId, UUID ownerId)
	{
		return this.gamePlayRepository.findPlayStatsByGame(gameId, ownerId).orElse(null);
	}

	/**
	 * Combines a saved game and its play totals into the details sent back to the page.
	 *
	 * @param game supplies the saved game details
	 * @param stats supplies the play totals; null means zero plays and no last-played date
	 * @return the game details ready to send to the frontend
	 */
	private BoardGameResponse toResponse(BoardGame game, PlayStats stats)
	{
		if (stats == null)
		{
			return BoardGameResponse.from(game, 0, null);
		}

		return BoardGameResponse.from(game, stats.getPlayCount(), stats.getLastPlayedOn());
	}


	/**
	 * Checks and applies the submitted cover image, or removes the old cover if none was supplied.
	 *
	 * @param game the game whose cover to change
	 * @param coverImage the image as a data URL; null or blank removes the cover
	 */
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

	/**
	 * Finds a game only within the requested user's collection.
	 * Another user's game is reported as missing rather than revealing that it exists.
	 *
	 * @param ownerId identifies the user whose collection to search
	 * @param gameId identifies the game to find
	 * @return the game belonging to that user
	 * @throws ResponseStatusException with HTTP 404 if no matching game is found
	 */
	private BoardGame findOwnedGame(UUID ownerId, UUID gameId)
	{
		return this.boardGameRepository.findByIdAndOwner_Id(gameId, ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	/**
	 * Removes surrounding spaces and stores a missing edition as empty text.
	 *
	 * @param edition the optional edition entered in the form
	 * @return the trimmed edition, or empty text if none was supplied
	 */
	private String cleanEdition(String edition)
	{
		return edition == null ? "" : edition.trim();
	}

	/**
	 * Writes a game to the database before continuing, so duplicate-name checks run immediately.
	 * This also catches duplicates that slip past the earlier check, such as two simultaneous saves.
	 *
	 * @param game the new or edited game to save
	 * @return the saved game
	 * @throws ResponseStatusException with HTTP 409 if the database rejects the save as a conflict
	 */
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

	/**
	 * Builds the error explaining that this name and edition are already in the collection.
	 *
	 * @return an HTTP 409 error with a message suggesting the Edition field for a different version
	 */
	private ResponseStatusException duplicateGame()
	{
		return new ResponseStatusException(HttpStatus.CONFLICT,
				"You already have a game with this name and edition. If this is a different version, add it using the Edition field.");
	}

	/**
	 * Checks that the maximum player count is not smaller than the minimum when both are entered.
	 *
	 * @param request contains the player counts entered in the game form
	 * @throws ResponseStatusException with HTTP 400 if the maximum is smaller than the minimum
	 */
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
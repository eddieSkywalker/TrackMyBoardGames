package com.SwampDragonInn.trackmyboardgames.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.SwampDragonInn.trackmyboardgames.api.BoardGameRequest;
import com.SwampDragonInn.trackmyboardgames.api.BoardGameResponse;
import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;
import com.SwampDragonInn.trackmyboardgames.repository.BoardGameRepository;
import com.SwampDragonInn.trackmyboardgames.repository.GamePlayRepository;
import com.SwampDragonInn.trackmyboardgames.repository.GamePlayRepository.PlayStats;

// Applies game rules and ensures every database operation is limited to the signed-in user's profile.
@Service
public class BoardGameService
{

	private final BoardGameRepository boardGameRepository;
	private final GamePlayRepository gamePlayRepository;
	private final UserProfileService userProfileService;

	public BoardGameService(BoardGameRepository boardGameRepository, GamePlayRepository gamePlayRepository,
			UserProfileService userProfileService)
	{
		this.boardGameRepository = boardGameRepository;
		this.gamePlayRepository = gamePlayRepository;
		this.userProfileService = userProfileService;
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
		BoardGame game = new BoardGame(owner, request.name().trim(), request.minPlayers(),
				request.maxPlayers(), request.bestPlayers(), request.playTimeMinutes(), request.notes());

		return toResponse(this.boardGameRepository.save(game), null);
	}

	// Validate and replace the details of an existing game owned by this user.
	@Transactional
	public BoardGameResponse update(String subjectIssuer, String subject, UUID gameId, BoardGameRequest request)
	{
		validatePlayerRange(request);
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		BoardGame game = findOwnedGame(owner.getId(), gameId);
		game.updateDetails(request.name().trim(), request.minPlayers(), request.maxPlayers(),
				request.bestPlayers(), request.playTimeMinutes(), request.notes());

		return toResponse(this.boardGameRepository.save(game), loadStats(gameId, owner.getId()));
	}

	// Delete the requested game only if it belongs to the authenticated user.
	@Transactional
	public void delete(String subjectIssuer, String subject, UUID gameId)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		this.boardGameRepository.delete(findOwnedGame(owner.getId(), gameId));
	}

	// Get the owner's games in name order; a blank search means no filtering.
	private List<BoardGame> findGamesByOwner(UUID ownerId, String search)
	{
		boolean hasSearch = search != null && !search.isBlank();

		if (hasSearch)
		{
			return this.boardGameRepository.findAllByOwner_IdAndNameContainingIgnoreCaseOrderByNameAsc(
					ownerId, search.trim());
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
		return stats == null
				? BoardGameResponse.from(game, 0, null)
				: BoardGameResponse.from(game, stats.getPlayCount(), stats.getLastPlayedOn());
	}

	// Treat another user's game the same as a missing game so its existence is not exposed.
	private BoardGame findOwnedGame(UUID ownerId, UUID gameId)
	{
		return this.boardGameRepository.findByIdAndOwner_Id(gameId, ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
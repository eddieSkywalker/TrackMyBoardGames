package com.SwampDragonInn.trackmyboardgames.service;

import java.util.List;
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

// Applies game rules and ensures every database operation is limited to the signed-in user's profile.
@Service
public class BoardGameService
{

	private final BoardGameRepository boardGameRepository;
	private final UserProfileService userProfileService;

	public BoardGameService(BoardGameRepository boardGameRepository, UserProfileService userProfileService)
	{
		this.boardGameRepository = boardGameRepository;
		this.userProfileService = userProfileService;
	}

	// List the owner's games alphabetically, optionally filtering by name.
	@Transactional
	public List<BoardGameResponse> findAll(String subjectIssuer, String subject, String search)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		List<BoardGame> games = search == null || search.isBlank()
				? this.boardGameRepository.findAllByOwner_IdOrderByNameAsc(owner.getId())
				: this.boardGameRepository.findAllByOwner_IdAndNameContainingIgnoreCaseOrderByNameAsc(
						owner.getId(), search.trim());

		return games.stream()
			.map(game -> BoardGameResponse.from(game))
			.toList();
	}

	// Return one game only when it belongs to the authenticated user.
	@Transactional
	public BoardGameResponse findById(String subjectIssuer, String subject, UUID gameId)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);

		return BoardGameResponse.from(findOwnedGame(owner.getId(), gameId));
	}

	// Validate the submitted details and save the new game under the authenticated user.
	@Transactional
	public BoardGameResponse create(String subjectIssuer, String subject, BoardGameRequest request)
	{
		validatePlayerRange(request);
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		BoardGame game = new BoardGame(owner, request.name().trim(), request.minPlayers(),
				request.maxPlayers(), request.playTimeMinutes(), request.notes());

		return BoardGameResponse.from(this.boardGameRepository.save(game));
	}

	// Validate and replace the details of an existing game owned by this user.
	@Transactional
	public BoardGameResponse update(String subjectIssuer, String subject, UUID gameId, BoardGameRequest request)
	{
		validatePlayerRange(request);
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		BoardGame game = findOwnedGame(owner.getId(), gameId);
		game.updateDetails(request.name().trim(), request.minPlayers(), request.maxPlayers(),
				request.playTimeMinutes(), request.notes());

		return BoardGameResponse.from(this.boardGameRepository.save(game));
	}

	// Delete the requested game only if it belongs to the authenticated user.
	@Transactional
	public void delete(String subjectIssuer, String subject, UUID gameId)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		this.boardGameRepository.delete(findOwnedGame(owner.getId(), gameId));
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
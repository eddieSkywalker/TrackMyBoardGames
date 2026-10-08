package com.SwampDragonInn.trackmyboardgames.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.SwampDragonInn.trackmyboardgames.api.GamePlayRequest;
import com.SwampDragonInn.trackmyboardgames.api.GamePlayResponse;
import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;
import com.SwampDragonInn.trackmyboardgames.domain.GamePlay;
import com.SwampDragonInn.trackmyboardgames.repository.BoardGameRepository;
import com.SwampDragonInn.trackmyboardgames.repository.GamePlayRepository;

// Applies play rules and ensures every operation is limited to games owned by the signed-in user.
@Service
public class GamePlayService
{

	private final GamePlayRepository gamePlayRepository;
	private final BoardGameRepository boardGameRepository;
	private final UserProfileService userProfileService;

	public GamePlayService(GamePlayRepository gamePlayRepository, BoardGameRepository boardGameRepository,
			UserProfileService userProfileService)
	{
		this.gamePlayRepository = gamePlayRepository;
		this.boardGameRepository = boardGameRepository;
		this.userProfileService = userProfileService;
	}

	// List a game's plays, newest first.
	@Transactional
	public List<GamePlayResponse> findAll(String subjectIssuer, String subject, UUID gameId)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		findOwnedGame(owner.getId(), gameId);

		return this.gamePlayRepository
				.findAllByBoardGame_IdAndBoardGame_Owner_IdOrderByPlayedOnDescCreatedAtDesc(gameId, owner.getId())
				.stream()
				.map(play -> GamePlayResponse.from(play))
				.toList();
	}

	// Return one play only when its game belongs to the authenticated user.
	@Transactional
	public GamePlayResponse findById(String subjectIssuer, String subject, UUID gameId, UUID playId)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);

		return GamePlayResponse.from(findOwnedPlay(owner.getId(), gameId, playId));
	}

	// Validate the submitted details and record a new play for one of the user's games.
	@Transactional
	public GamePlayResponse create(String subjectIssuer, String subject, UUID gameId, GamePlayRequest request)
	{
		validatePlayedOn(request);
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		BoardGame game = findOwnedGame(owner.getId(), gameId);
		GamePlay play = new GamePlay(game, request.playedOn(), request.durationMinutes(),
				request.playerCount(), request.notes());

		return GamePlayResponse.from(this.gamePlayRepository.save(play));
	}

	// Validate and replace the details of an existing play.
	@Transactional
	public GamePlayResponse update(String subjectIssuer, String subject, UUID gameId, UUID playId,
			GamePlayRequest request)
	{
		validatePlayedOn(request);
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		GamePlay play = findOwnedPlay(owner.getId(), gameId, playId);
		play.updateDetails(request.playedOn(), request.durationMinutes(), request.playerCount(), request.notes());

		return GamePlayResponse.from(this.gamePlayRepository.save(play));
	}

	// Delete a play only when its game belongs to the authenticated user.
	@Transactional
	public void delete(String subjectIssuer, String subject, UUID gameId, UUID playId)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		this.gamePlayRepository.delete(findOwnedPlay(owner.getId(), gameId, playId));
	}

	// Treat another user's game the same as a missing game so its existence is not exposed.
	private BoardGame findOwnedGame(UUID ownerId, UUID gameId)
	{
		return this.boardGameRepository.findByIdAndOwner_Id(gameId, ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	// Find a play by id only if it belongs to the given game and that game belongs to the user;
	// anything else is reported as missing so other users' plays are never revealed.
	private GamePlay findOwnedPlay(UUID ownerId, UUID gameId, UUID playId)
	{
		return this.gamePlayRepository.findByIdAndBoardGame_IdAndBoardGame_Owner_Id(playId, gameId, ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	// Reject future dates; one day of slack avoids rejecting "today" for users ahead of the server's time zone.
	private void validatePlayedOn(GamePlayRequest request)
	{
		if (request.playedOn().isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(1)))
		{
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The played date cannot be in the future.");
		}
	}
}

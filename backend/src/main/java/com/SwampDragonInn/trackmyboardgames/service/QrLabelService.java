package com.SwampDragonInn.trackmyboardgames.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.SwampDragonInn.trackmyboardgames.api.BoardGameResponse;
import com.SwampDragonInn.trackmyboardgames.api.QrLabelResponse;
import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;
import com.SwampDragonInn.trackmyboardgames.domain.QrLabelCode;
import com.SwampDragonInn.trackmyboardgames.repository.BoardGameRepository;
import com.SwampDragonInn.trackmyboardgames.repository.QrLabelCodeRepository;

// Creates, revokes, lists and resolves QR labels, always limited to the signed-in user's own games.
@Service
public class QrLabelService
{

	private static final SecureRandom RANDOM = new SecureRandom();

	private final QrLabelCodeRepository qrLabelCodeRepository;
	private final BoardGameRepository boardGameRepository;
	private final BoardGameService boardGameService;
	private final UserProfileService userProfileService;

	public QrLabelService(QrLabelCodeRepository qrLabelCodeRepository, BoardGameRepository boardGameRepository,
			BoardGameService boardGameService, UserProfileService userProfileService)
	{
		this.qrLabelCodeRepository = qrLabelCodeRepository;
		this.boardGameRepository = boardGameRepository;
		this.boardGameService = boardGameService;
		this.userProfileService = userProfileService;
	}

	// List every label the user has made (active and revoked), newest first.
	@Transactional
	public List<QrLabelResponse> findAll(String subjectIssuer, String subject)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		List<QrLabelCode> labels = this.qrLabelCodeRepository.findAllByOwner_IdOrderByCreatedAtDesc(owner.getId());

		List<QrLabelResponse> responses = new ArrayList<>();
		for (QrLabelCode label : labels)
		{
			responses.add(QrLabelResponse.from(label));
		}
		return responses;
	}

	// Make a new label with a fresh random token for one of the user's games.
	@Transactional
	public QrLabelResponse create(String subjectIssuer, String subject, UUID gameId)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		BoardGame game = findOwnedGame(owner.getId(), gameId);

		QrLabelCode label = new QrLabelCode(owner, game, newToken());
		QrLabelCode savedLabel = this.qrLabelCodeRepository.save(label);
		return QrLabelResponse.from(savedLabel);
	}

	// Stop a label from working. Revoking an already revoked label does nothing.
	@Transactional
	public void revoke(String subjectIssuer, String subject, UUID gameId, UUID labelId)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		QrLabelCode label = findOwnedLabel(owner.getId(), gameId, labelId);

		label.revoke();
	}

	// Turn a scanned token into the game it points at. Unknown, revoked, and other users' tokens all look the same (404).
	@Transactional
	public BoardGameResponse resolve(String subjectIssuer, String subject, String token)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		QrLabelCode label = findActiveLabelByToken(owner.getId(), token);

		UUID gameId = label.getBoardGame().getId();
		return this.boardGameService.findById(subjectIssuer, subject, gameId);
	}

	// Treat another user's game the same as a missing game so its existence is not exposed.
	private BoardGame findOwnedGame(UUID ownerId, UUID gameId)
	{
		Optional<BoardGame> game = this.boardGameRepository.findByIdAndOwner_Id(gameId, ownerId);
		if (game.isEmpty())
		{
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return game.get();
	}

	// Find a label by id only if it belongs to the given game and the user; anything else is reported as missing.
	private QrLabelCode findOwnedLabel(UUID ownerId, UUID gameId, UUID labelId)
	{
		Optional<QrLabelCode> label = this.qrLabelCodeRepository.findByIdAndBoardGame_IdAndOwner_Id(labelId, gameId, ownerId);
		if (label.isEmpty())
		{
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return label.get();
	}

	// Find the user's label for a token, treating a revoked label the same as a missing one.
	private QrLabelCode findActiveLabelByToken(UUID ownerId, String token)
	{
		Optional<QrLabelCode> label = this.qrLabelCodeRepository.findByTokenAndOwner_Id(token, ownerId);
		if (label.isEmpty() || label.get().isRevoked())
		{
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return label.get();
	}

	// 32 random bytes (256 bits) written as URL-safe text, 43 characters; impossible to guess.
	private String newToken()
	{
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
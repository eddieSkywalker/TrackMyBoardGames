package com.SwampDragonInn.trackmyboardgames.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SwampDragonInn.trackmyboardgames.domain.QrLabelCode;

// Database access for QR labels; every query includes the owner so users only reach their own labels.
public interface QrLabelCodeRepository extends JpaRepository<QrLabelCode, UUID>
{

	// All of one user's labels, newest first.
	List<QrLabelCode> findAllByOwner_IdOrderByCreatedAtDesc(UUID ownerId);

	Optional<QrLabelCode> findByIdAndBoardGame_IdAndOwner_Id(UUID id, UUID boardGameId, UUID ownerId);

	Optional<QrLabelCode> findByTokenAndOwner_Id(String token, UUID ownerId);
}
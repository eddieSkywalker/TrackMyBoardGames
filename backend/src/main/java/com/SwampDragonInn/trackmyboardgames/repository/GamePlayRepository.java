package com.SwampDragonInn.trackmyboardgames.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SwampDragonInn.trackmyboardgames.domain.GamePlay;

// Database access for plays; every query includes the game and its owner so users only reach their own plays.
public interface GamePlayRepository extends JpaRepository<GamePlay, UUID>
{

	List<GamePlay> findAllByBoardGame_IdAndBoardGame_Owner_IdOrderByPlayedOnDescCreatedAtDesc(UUID boardGameId, UUID ownerId);

	Optional<GamePlay> findByIdAndBoardGame_IdAndBoardGame_Owner_Id(UUID id, UUID boardGameId, UUID ownerId);
}

package com.SwampDragonInn.trackmyboardgames.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;

// Database access for games; query methods include the owner so users only get their own records.
public interface BoardGameRepository extends JpaRepository<BoardGame, UUID>
{

	List<BoardGame> findAllByOwner_IdOrderByNameAsc(UUID ownerId);

	List<BoardGame> findAllByOwner_IdAndNameContainingIgnoreCaseOrderByNameAsc(UUID ownerId, String name);

	Optional<BoardGame> findByIdAndOwner_Id(UUID id, UUID ownerId);
}
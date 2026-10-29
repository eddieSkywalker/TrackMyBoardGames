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

	/**
	 * Counts only games in the chosen owner's collection when checking its game allowance.
	 */
	long countByOwner_IdAndCollection_Id(UUID ownerId, UUID collectionId);

	/**
	 * Lists games only from the chosen owner's collection.
	 */
	List<BoardGame> findAllByOwner_IdAndCollection_IdOrderByNameAsc(UUID ownerId, UUID collectionId);

	/**
	 * Searches game names within the chosen owner's collection.
	 */
	List<BoardGame> findAllByOwner_IdAndCollection_IdAndNameContainingIgnoreCaseOrderByNameAsc(
			UUID ownerId, UUID collectionId, String name);

	List<BoardGame> findAllByOwner_IdAndNameContainingIgnoreCaseOrderByNameAsc(UUID ownerId, String name);

	// True when the owner already has a game with this name and edition (compared ignoring upper/lower case).
	boolean existsByOwner_IdAndNameIgnoreCaseAndEditionIgnoreCase(UUID ownerId, String name, String edition);

	// Same check for an edit, ignoring the game being edited so saving it unchanged is allowed.
	boolean existsByOwner_IdAndNameIgnoreCaseAndEditionIgnoreCaseAndIdNot(UUID ownerId, String name, String edition, UUID id);

	Optional<BoardGame> findByIdAndOwner_Id(UUID id, UUID ownerId);
}
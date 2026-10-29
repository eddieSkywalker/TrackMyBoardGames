package com.SwampDragonInn.trackmyboardgames.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.SwampDragonInn.trackmyboardgames.domain.GameCollection;

/**
 * Provides owner-checked collection lookups so requests only reach the signed-in user's collections.
 */
public interface GameCollectionRepository extends JpaRepository<GameCollection, UUID>
{

	/**
	 * Finds the oldest collection to reuse when a profile has no saved default.
	 */
	Optional<GameCollection> findFirstByOwner_IdOrderByCreatedAtAscIdAsc(UUID ownerId);

	/**
	 * Lists owned collections oldest first, using their IDs to break creation-time ties.
	 */
	List<GameCollection> findAllByOwner_IdOrderByCreatedAtAscIdAsc(UUID ownerId);

	/**
	 * Treats someone else's collection as missing rather than exposing their details.
	 */
	Optional<GameCollection> findByIdAndOwner_Id(UUID id, UUID ownerId);

	/**
	 * Makes payments and new-game saves for the same collection take turns.
	 * This keeps payment totals current and prevents two requests from taking the last game slot.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from GameCollection c where c.id = :collectionId and c.owner.id = :ownerId")
	Optional<GameCollection> findForUpdate(@Param("collectionId") UUID collectionId, @Param("ownerId") UUID ownerId);
}

package com.SwampDragonInn.trackmyboardgames.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SwampDragonInn.trackmyboardgames.domain.GameCollection;

// Database access for collections; lookups always include the owner so users only reach their own.
public interface GameCollectionRepository extends JpaRepository<GameCollection, UUID>
{

	// With only one collection per user today, this returns that one (the oldest if there are ever several).
	Optional<GameCollection> findFirstByOwner_IdOrderByCreatedAtAscIdAsc(UUID ownerId);
}

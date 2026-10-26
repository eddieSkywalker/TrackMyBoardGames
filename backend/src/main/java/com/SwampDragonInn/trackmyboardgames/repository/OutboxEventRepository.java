package com.SwampDragonInn.trackmyboardgames.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.SwampDragonInn.trackmyboardgames.domain.OutboxEvent;

import jakarta.persistence.LockModeType;

// Reads and writes the outbox table in PostgreSQL. The dispatcher uses this repository indirectly to find the
// oldest event that has not been sent yet and is ready for another attempt.
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID>
{

	// Finds the oldest due event and locks its row while a worker reserves it, preventing two workers from taking it together.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select event from OutboxEvent event
			where event.publishedAt is null
				and event.attemptCount < :maxAttempts
				and event.nextAttemptAt <= :now
				and (event.claimedUntil is null or event.claimedUntil <= :now)
			order by event.occurredAt, event.id
			""")
	// Each claim reserves one event; exhausted events stay saved but are excluded from automatic publishing.
	List<OutboxEvent> findNextPublishable(
			@Param("now") Instant now, @Param("maxAttempts") int maxAttempts, Pageable pageable);
}

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

// Internal persistence only: outbox payloads must not be exposed as a user-facing API.
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID>
{

	// Lock the next eligible row so concurrent backend instances cannot claim it at the same time.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select event from OutboxEvent event
			where event.publishedAt is null
				and event.nextAttemptAt <= :now
				and (event.claimedUntil is null or event.claimedUntil <= :now)
			order by event.occurredAt, event.id
			""")
	List<OutboxEvent> findNextPublishable(@Param("now") Instant now, Pageable pageable);
}

package com.SwampDragonInn.trackmyboardgames.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.domain.StatisticsProcessedEvent;

// Remembers which events statistics has handled. The future receiver must save this receipt and its
// statistics changes in the same transaction, so a failed update does not leave a false success receipt.
public interface StatisticsProcessedEventRepository extends Repository<StatisticsProcessedEvent, UUID>
{

	// Returns 1 for a new event or 0 for a repeat. PostgreSQL also protects against simultaneous deliveries.
	// An existing transaction is required: if the statistics update fails, this receipt must roll back too.
	@Modifying
	@Transactional(propagation = Propagation.MANDATORY)
	@Query(value = """
			insert into statistics_processed_events (event_id, processed_at)
			values (:eventId, :processedAt)
			on conflict (event_id) do nothing
			""", nativeQuery = true)
	int recordIfNew(@Param("eventId") UUID eventId, @Param("processedAt") Instant processedAt);

	// Looks up a saved receipt without needing the original game, play, or outbox row.
	@Transactional(readOnly = true)
	Optional<StatisticsProcessedEvent> findById(UUID eventId);
}

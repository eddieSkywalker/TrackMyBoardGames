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

/**
 * Reads and writes event receipts in {@code statistics_processed_events}, not {@code outbox_events}.
 *
 * <p>A receipt means the statistics service has handled that event already. The service saves a new receipt and
 * the updated statistics in one transaction: either both commit, or both roll back. This prevents a
 * repeated RabbitMQ delivery from recalculating statistics for the same event.
 */
public interface StatisticsProcessedEventRepository extends Repository<StatisticsProcessedEvent, UUID>
{

	/**
	 * Tries to record an event receipt as part of the statistics service's existing transaction.
	 *
	 * <p>The event ID comes from the RabbitMQ message and matches the original outbox event ID.
	 * This query does not read the outbox table. PostgreSQL's unique event ID prevents duplicate receipts,
	 * even if two deliveries try to record the same event at the same time.
	 *
	 * @param eventId ID of the event being handled
	 * @param processedAt time recorded for this processing attempt
	 * @return 1 if a new receipt was inserted, or 0 if a receipt already exists and calculation should be skipped
	 */
	@Modifying
	@Transactional(propagation = Propagation.MANDATORY)
	@Query(value = """
			insert into statistics_processed_events (event_id, processed_at)
			values (:eventId, :processedAt)
			on conflict (event_id) do nothing
			""", nativeQuery = true)
	int recordIfNew(@Param("eventId") UUID eventId, @Param("processedAt") Instant processedAt);

	/**
	 * Looks up a saved processing receipt without reading the original game, play, or outbox row.
	 *
	 * @param eventId ID of the event whose receipt to find
	 * @return the saved receipt, or empty if none exists
	 */
	@Transactional(readOnly = true)
	Optional<StatisticsProcessedEvent> findById(UUID eventId);
}

package com.SwampDragonInn.trackmyboardgames.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.repository.AppUserRepository;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsProcessedEventRepository;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsRefreshQueryRepository;

/**
 * Coordinates rebuilding saved statistics, primarily after RabbitMQ delivers game or play changes.
 *
 * <p>Calls a PostgreSQL procedure to calculate and save results from current game and play records.
 * Also supports startup backfill without RabbitMQ. Frontend requests use StatisticsReadService instead.
 * Events prompt a recount rather than adding or subtracting values from their payloads,
 * so edits, deletions, and late-arriving events use the current records.
 *
 * <p>For event processing, the saved statistics and event receipt commit together.
 * A failed refresh rolls both back so a later delivery can retry.
 */
@Service
public class StatisticsRefreshService
{

	private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsRefreshService.class);
	private final AppUserRepository users;
	private final StatisticsRefreshQueryRepository refreshQueries;
	private final StatisticsProcessedEventRepository processedEvents;

	/**
	 * Connects owner locking, database procedure calls, and duplicate-event receipts.
	 *
	 * @param users owner lookup and locking
	 * @param refreshQueries procedures that calculate and save statistics in PostgreSQL
	 * @param processedEvents duplicate-event receipts
	 */
	public StatisticsRefreshService(AppUserRepository users, StatisticsRefreshQueryRepository refreshQueries,
			StatisticsProcessedEventRepository processedEvents)
	{
		this.users = users;
		this.refreshQueries = refreshQueries;
		this.processedEvents = processedEvents;
	}

	/**
	 * Refreshes one owner's saved statistics for an event validated by the message consumer.
	 *
	 * <p>The owner lock serializes refreshes until this transaction commits or rolls back.
	 * The event receipt is recorded in the same transaction as the replacement statistics.
	 *
	 * @param eventId unique ID of the delivered event
	 * @param ownerId owner whose statistics to rebuild
	 * @return true if refreshed, or false if the event was already processed
	 * @throws NullPointerException if either identifier is null
	 * @throws IllegalStateException if the owner no longer exists
	 */
	@Transactional
	public boolean refresh(UUID eventId, UUID ownerId)
	{
		Objects.requireNonNull(eventId, "Event ID is required.");
		Objects.requireNonNull(ownerId, "Owner ID is required.");

		// Wait our turn before recalculating this owner's statistics. The database lock stays
		// in place until this whole transaction succeeds or rolls back.
		this.users
			.findForStatisticsRefresh(ownerId)
			.orElseThrow(() -> new IllegalStateException("Statistics event owner does not exist."));

		// Skip a repeated event. If a later step fails, this new receipt rolls back as well.
		Instant now = Instant.now();
		if (this.processedEvents.recordIfNew(eventId, now) == 0)
		{
			return false;
		}

		this.refreshQueries.refreshCollectionStatistics(ownerId, now);
		return true;
	}

	/**
	 * Rebuilds an owner's statistics without creating an event or processing receipt.
	 *
	 * <p>Uses the same owner lock as event-driven refreshes to prevent competing saves.
	 * The backfill runner calls this separately for each owner, giving each its own transaction.
	 *
	 * @param ownerId owner whose statistics to rebuild
	 * @return true if rebuilt, or false if the owner was deleted before processing
	 * @throws NullPointerException if the owner ID is null
	 */
	@Transactional
	public boolean backfill(UUID ownerId)
	{
		Objects.requireNonNull(ownerId, "Owner ID is required.");
		if (this.users.findForStatisticsRefresh(ownerId).isEmpty())
		{
			// An account may be deleted after the backfill reads its ID but before it reaches this step.
			LOGGER.warn("Skipping statistics backfill for owner {} because the account no longer exists.", ownerId);
			return false;
		}
		this.refreshQueries.refreshCollectionStatistics(ownerId, Instant.now());
		return true;
	}

}

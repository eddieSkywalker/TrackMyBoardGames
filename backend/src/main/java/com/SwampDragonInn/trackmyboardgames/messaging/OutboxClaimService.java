package com.SwampDragonInn.trackmyboardgames.messaging;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.domain.OutboxEvent;
import com.SwampDragonInn.trackmyboardgames.repository.OutboxEventRepository;

/**
 * Handles the short database steps around sending outbox events.
 *
 * <p>It reserves a due event and records whether it was published or needs another attempt. The dispatcher
 * communicates with RabbitMQ separately, so a database row is not locked during a network call.
 */
@Service
public class OutboxClaimService
{

	private static final Logger LOGGER = LoggerFactory.getLogger(OutboxClaimService.class);
	private static final int MAX_PUBLISH_ATTEMPTS = 5;
	private static final long CLAIM_LEASE_SECONDS = 30;

	private final OutboxEventRepository repository;

	/**
	 * Creates the service that reserves and updates saved outbox events.
	 *
	 * @param repository reads and updates outbox event rows
	 */
	public OutboxClaimService(OutboxEventRepository repository)
	{
		this.repository = repository;
	}

	/**
	 * Finds one event ready to send and reserves it for 30 seconds.
	 * Events that have already reached five attempts are left in the table for investigation.
	 *
	 * @param now current time used to determine whether an event is ready
	 * @return the reserved event, or empty if no event is ready
	 */
	@Transactional
	public Optional<OutboxEvent> claimNext(Instant now)
	{
		return this.repository.findNextPublishable(now, MAX_PUBLISH_ATTEMPTS, PageRequest.of(0, 1))
				.stream()
				.findFirst()
				.map(event ->
				{
					event.claim(UUID.randomUUID(), now.plusSeconds(CLAIM_LEASE_SECONDS));
					return event;
				});
	}

	/**
	 * Records RabbitMQ's successful confirmation and releases the event's reservation.
	 *
	 * @param eventId ID of the event that RabbitMQ confirmed
	 * @param claimToken token proving this worker still owns the event claim
	 * @param publishedAt time RabbitMQ confirmed publication
	 */
	@Transactional
	public void markPublished(UUID eventId, UUID claimToken, Instant publishedAt)
	{
		OutboxEvent event = this.repository.findById(eventId).orElseThrow();
		event.markPublished(claimToken, publishedAt);
	}

	/**
	 * Records a failed publish attempt and when the event may be attempted again.
	 *
	 * @param eventId ID of the event that failed to publish
	 * @param claimToken token proving this worker still owns the event claim
	 * @param nextAttemptAt earliest time the event may be retried
	 * @param errorType exception type from the failed publish attempt
	 */
	@Transactional
	public void scheduleRetry(UUID eventId, UUID claimToken, Instant nextAttemptAt, String errorType)
	{
		OutboxEvent event = this.repository.findById(eventId).orElseThrow();
		event.scheduleRetry(claimToken, nextAttemptAt, errorType);
		if (event.getAttemptCount() >= MAX_PUBLISH_ATTEMPTS)
		{
			LOGGER.error("Outbox event {} reached {} publish attempts; automatic publishing stopped ({})",
					eventId, MAX_PUBLISH_ATTEMPTS, errorType);
		}
	}
}

package com.SwampDragonInn.trackmyboardgames.messaging;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.domain.OutboxEvent;
import com.SwampDragonInn.trackmyboardgames.repository.OutboxEventRepository;

// Handles the short database steps around sending an event: reserve a due event, then save whether it was sent
// or needs another try. The dispatcher talks to RabbitMQ separately, so the database row is not locked during
// a network call.
@Service
public class OutboxClaimService
{

	private static final long CLAIM_LEASE_SECONDS = 30;

	private final OutboxEventRepository repository;

	// Uses the repository that reads and updates saved outbox events.
	public OutboxClaimService(OutboxEventRepository repository)
	{
		this.repository = repository;
	}

	// Finds one event ready to send and reserves it for 30 seconds so another worker will not send it too.
	@Transactional
	public Optional<OutboxEvent> claimNext(Instant now)
	{
		return this.repository.findNextPublishable(now, PageRequest.of(0, 1))
				.stream()
				.findFirst()
				.map(event ->
				{
					event.claim(UUID.randomUUID(), now.plusSeconds(CLAIM_LEASE_SECONDS));
					return event;
				});
	}

	// Saves RabbitMQ's successful confirmation and releases the event's reservation.
	@Transactional
	public void markPublished(UUID eventId, UUID claimToken, Instant publishedAt)
	{
		OutboxEvent event = this.repository.findById(eventId).orElseThrow();
		event.markPublished(claimToken, publishedAt);
	}

	// Saves the failed attempt and when the sender is allowed to try this event again.
	@Transactional
	public void scheduleRetry(UUID eventId, UUID claimToken, Instant nextAttemptAt, String errorType)
	{
		OutboxEvent event = this.repository.findById(eventId).orElseThrow();
		event.scheduleRetry(claimToken, nextAttemptAt, errorType);
	}
}

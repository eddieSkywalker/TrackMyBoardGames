package com.SwampDragonInn.trackmyboardgames.messaging;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.domain.OutboxEvent;
import com.SwampDragonInn.trackmyboardgames.repository.OutboxEventRepository;

// Short database transactions claim events and record publish results; no broker call happens while a DB lock is held.
@Service
public class OutboxClaimService
{

	private static final long CLAIM_LEASE_SECONDS = 30;

	private final OutboxEventRepository repository;

	public OutboxClaimService(OutboxEventRepository repository)
	{
		this.repository = repository;
	}

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

	@Transactional
	public void markPublished(UUID eventId, UUID claimToken, Instant publishedAt)
	{
		OutboxEvent event = this.repository.findById(eventId).orElseThrow();
		event.markPublished(claimToken, publishedAt);
	}

	@Transactional
	public void scheduleRetry(UUID eventId, UUID claimToken, Instant nextAttemptAt, String errorType)
	{
		OutboxEvent event = this.repository.findById(eventId).orElseThrow();
		event.scheduleRetry(claimToken, nextAttemptAt, errorType);
	}
}

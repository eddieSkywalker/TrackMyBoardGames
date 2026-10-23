package com.SwampDragonInn.trackmyboardgames.messaging;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.SwampDragonInn.trackmyboardgames.domain.OutboxEvent;
import com.rabbitmq.client.ShutdownSignalException;

// Sends one event at a time after the polling worker asks it to. It does not watch for database changes itself:
// it asks PostgreSQL for an event that is due, sends it to RabbitMQ, then saves success or a retry time.
@Service
@ConditionalOnProperty(prefix = "app.messaging.rabbitmq", name = "enabled", havingValue = "true")
public class OutboxDispatcher
{

	private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatcher.class);
	private static final long INITIAL_RETRY_SECONDS = 5;
	private static final long MAX_RETRY_SECONDS = 300;

	private final OutboxClaimService claimService;
	private final RabbitMqPublisher publisher;

	// Uses the database service to reserve and update events, and the publisher to send them to RabbitMQ.
	public OutboxDispatcher(OutboxClaimService claimService, RabbitMqPublisher publisher)
	{
		this.claimService = claimService;
		this.publisher = publisher;
	}

	// Tries to send one event that is ready. Returns false if there is no event to send right now.
	public boolean publishNext()
	{
		Optional<OutboxEvent> nextEvent = this.claimService.claimNext(Instant.now());
		if (nextEvent.isEmpty())
		{
			return false;
		}
		OutboxEvent event = nextEvent.get();

		try
		{
			// The DB claim has committed before this network call, so we do not hold a DB lock while waiting.
			this.publisher.publish(event);
		}
		catch (IOException | InterruptedException | TimeoutException | ShutdownSignalException publishFailure)
		{
			long retryDelay = retryDelaySeconds(event.getAttemptCount());
			try
			{
				this.claimService.scheduleRetry(event.getId(), event.getClaimToken(),
						Instant.now().plusSeconds(retryDelay), publishFailure.getClass().getSimpleName());
			}
			finally
			{
				if (publishFailure instanceof InterruptedException)
				{
					Thread.currentThread().interrupt();
				}
			}

			// Log event identity and exception type only; do not log a URI that could contain credentials.
			LOGGER.warn("Could not publish outbox event {} ({}); retry scheduled in {} seconds ({})",
					event.getId(), event.getEventType(), retryDelay, publishFailure.getClass().getSimpleName());
			return true;
		}

		// A DB failure here is allowed to escape. The lease will expire and retry later, possibly duplicating
		// a message RabbitMQ already accepted; the future consumer must therefore be idempotent.
		this.claimService.markPublished(event.getId(), event.getClaimToken(), Instant.now());
		LOGGER.info("Published outbox event {} ({})", event.getId(), event.getEventType());
		return true;
	}

	// Waits longer between retries after repeated failures, up to the five-minute limit.
	private long retryDelaySeconds(int attemptCount)
	{
		int exponent = Math.min(Math.max(attemptCount - 1, 0), 6);
		return Math.min(INITIAL_RETRY_SECONDS * (1L << exponent), MAX_RETRY_SECONDS);
	}
}

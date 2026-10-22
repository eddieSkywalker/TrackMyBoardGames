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

// Polls PostgreSQL, publishes one claimed event, then records success or schedules a retry.
@Service
@ConditionalOnProperty(prefix = "app.messaging.rabbitmq", name = "enabled", havingValue = "true")
public class OutboxDispatcher
{

	private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatcher.class);
	private static final long INITIAL_RETRY_SECONDS = 5;
	private static final long MAX_RETRY_SECONDS = 300;

	private final OutboxClaimService claimService;
	private final RabbitMqPublisher publisher;

	public OutboxDispatcher(OutboxClaimService claimService, RabbitMqPublisher publisher)
	{
		this.claimService = claimService;
		this.publisher = publisher;
	}

	// Returns false when there is no due event; otherwise reports that one event was attempted.
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

	private long retryDelaySeconds(int attemptCount)
	{
		int exponent = Math.min(Math.max(attemptCount - 1, 0), 6);
		return Math.min(INITIAL_RETRY_SECONDS * (1L << exponent), MAX_RETRY_SECONDS);
	}
}

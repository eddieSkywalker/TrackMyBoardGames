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

/**
 * Publishes due outbox events when the polling worker asks it to.
 *
 * <p>This dispatcher does not watch for database changes. It claims each event from PostgreSQL immediately before
 * sending it to RabbitMQ, then records whether the send succeeded or should be retried.
 */
@Service
@ConditionalOnProperty(prefix = "app.messaging.rabbitmq", name = "enabled", havingValue = "true")
public class OutboxDispatcher
{

	private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatcher.class);
	private static final long INITIAL_RETRY_SECONDS = 5;
	private static final long MAX_RETRY_SECONDS = 300;

	private final OutboxClaimService claimService;
	private final RabbitMqPublisher publisher;

	/**
	 * Creates a dispatcher using the service that claims outbox rows and the RabbitMQ publisher.
	 *
	 * @param claimService reserves events and records publish or retry outcomes
	 * @param publisher sends claimed events to RabbitMQ
	 */
	public OutboxDispatcher(OutboxClaimService claimService, RabbitMqPublisher publisher)
	{
		this.claimService = claimService;
		this.publisher = publisher;
	}

	/**
	 * Tries to publish one due event.
	 *
	 * @return {@code true} if an event was attempted; {@code false} if none was due
	 */
	public boolean publishNext()
	{
		return publishOne();
	}

	/**
	 * Publishes up to the requested number of due events, one at a time.
	 *
	 * <p>Each event is claimed immediately before publishing, so later events do not lose their claim leases while
	 * earlier messages wait for RabbitMQ confirmations.
	 *
	 * @param maxEvents maximum number of events to attempt in this polling run; must be positive
	 * @return the number of events attempted
	 */
	public int publishBatch(int maxEvents)
	{
		if (maxEvents < 1)
		{
			throw new IllegalArgumentException("Batch size must be at least 1.");
		}

		int attemptedEvents = 0;
		while (attemptedEvents < maxEvents)
		{
			if (!publishOne())
			{
				break;
			}
            
			attemptedEvents++;
            // Check if the current thread has been interrupted and exit the loop if so.
			if (Thread.currentThread().isInterrupted())
			{
				break;
			}
		}
		return attemptedEvents;
	}

    /** 
     * Attempts to publish the next due outbox event.
     */
	private boolean publishOne()
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
            // Schedule a retry for this event since publishing failed.
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
			LOGGER.warn("Could not publish outbox event {} ({}) on attempt {} ({})",
					event.getId(), event.getEventType(), event.getAttemptCount(), publishFailure.getClass().getSimpleName());
			return true;
		}

		// A DB failure here is allowed to escape. The lease will expire and retry later, possibly duplicating
		// a message RabbitMQ already accepted; the future consumer must therefore be idempotent.
		this.claimService.markPublished(event.getId(), event.getClaimToken(), Instant.now());
		LOGGER.info("Published outbox event {} ({})", event.getId(), event.getEventType());
		return true;
	}

	/**
	 * Calculates a retry delay that grows after repeated failures and stops at five minutes.
	 *
	 * @param attemptCount number of publish attempts made for the event
	 * @return delay, in seconds, before another attempt
	 */
	private long retryDelaySeconds(int attemptCount)
	{
		int exponent = Math.min(Math.max(attemptCount - 1, 0), 6);
		return Math.min(INITIAL_RETRY_SECONDS * (1L << exponent), MAX_RETRY_SECONDS);
	}
}

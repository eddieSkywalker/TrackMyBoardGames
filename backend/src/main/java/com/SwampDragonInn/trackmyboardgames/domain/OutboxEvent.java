package com.SwampDragonInn.trackmyboardgames.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// One change the app has saved and still needs to send to RabbitMQ. This class tracks sending and retries,
// not what the statistics service does after it receives the message.
@Entity
@Table(name = "outbox_events")
public class OutboxEvent
{

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "event_type", nullable = false, length = 100, updatable = false)
	private String eventType;

	@Column(name = "event_version", nullable = false, updatable = false)
	private int eventVersion;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private UUID ownerId;

	@Column(name = "aggregate_id", nullable = false, updatable = false)
	private UUID aggregateId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb", updatable = false)
	private String payload;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "claim_token")
	private UUID claimToken;

	@Column(name = "claimed_until")
	private Instant claimedUntil;

	@Column(name = "last_error", length = 2000)
	private String lastError;

	protected OutboxEvent()
	{
		// JPA needs an empty constructor so it can rebuild an event from a database row.
	}

	// Creates a new event that is ready for the sender to pick up.
	public OutboxEvent(
        String eventType, 
        int eventVersion, 
        UUID ownerId, 
        UUID aggregateId,
		String payload, 
        Instant occurredAt)
	{
		if (eventType == null || eventType.isBlank() || eventType.length() > 100)
		{
			throw new IllegalArgumentException("Event type must contain between 1 and 100 characters.");
		}

		if (eventVersion < 1)
		{
			throw new IllegalArgumentException("Event version must be positive.");
		}

		this.eventType = eventType;
		this.eventVersion = eventVersion;
		this.ownerId = Objects.requireNonNull(ownerId, "Owner ID is required.");
		this.aggregateId = Objects.requireNonNull(aggregateId, "Aggregate ID is required.");
		this.payload = Objects.requireNonNull(payload, "JSON payload is required.");
		this.occurredAt = Objects.requireNonNull(occurredAt, "Occurrence time is required.");

		// New events are immediately eligible for publication; no lease or success exists yet.
		this.nextAttemptAt = occurredAt;
	}

	// Returns this event's database ID.
	public UUID getId()
	{
		return this.id;
	}

	// Returns the name of the change, such as "GameUpdated".
	public String getEventType()
	{
		return this.eventType;
	}

	// Returns which version of this event's message format is being used.
	public int getEventVersion()
	{
		return this.eventVersion;
	}

	// Returns the ID of the user who owns the changed data.
	public UUID getOwnerId()
	{
		return this.ownerId;
	}

	// Returns the ID of the game or play that changed.
	public UUID getAggregateId()
	{
		return this.aggregateId;
	}

	// Returns the saved event details that will be sent to RabbitMQ.
	public String getPayload()
	{
		return this.payload;
	}

	// Returns when the original game or play change happened.
	public Instant getOccurredAt()
	{
		return this.occurredAt;
	}

	// Returns when RabbitMQ confirmed that this event was sent, or null if it has not been sent yet.
	public Instant getPublishedAt()
	{
		return this.publishedAt;
	}

	// Returns how many times the sender has tried to send this event.
	public int getAttemptCount()
	{
		return this.attemptCount;
	}

	// Returns the earliest time the sender may try to send this event again.
	public Instant getNextAttemptAt()
	{
		return this.nextAttemptAt;
	}

	// Returns the ID that proves which sender currently owns this event, or null if it is unclaimed.
	public UUID getClaimToken()
	{
		return this.claimToken;
	}

	// Returns when another sender may take over if the current sender stops responding.
	public Instant getClaimedUntil()
	{
		return this.claimedUntil;
	}

	// Returns the type of the last sending error, or null if there has not been one.
	public String getLastError()
	{
		return this.lastError;
	}

	// Reserves this event for one sender and counts the new sending attempt.
	public void claim(UUID claimToken, Instant claimedUntil)
	{
		if (this.publishedAt != null)
		{
			throw new IllegalStateException("A published event cannot be claimed.");
		}

		this.claimToken = Objects.requireNonNull(claimToken, "Claim token is required.");
		this.claimedUntil = Objects.requireNonNull(claimedUntil, "Claim expiry is required.");
		this.attemptCount++;
	}

	// Records that RabbitMQ confirmed the send and frees the event for no further attempts.
	public void markPublished(UUID claimToken, Instant publishedAt)
	{
		requireCurrentClaim(claimToken);
		this.publishedAt = Objects.requireNonNull(publishedAt, "Publication time is required.");
		clearClaim();
		this.lastError = null;
	}

	// Records a failed send so the sender can try again later.
	public void scheduleRetry(UUID claimToken, Instant nextAttemptAt, String errorType)
	{
		requireCurrentClaim(claimToken);
		this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "Next attempt time is required.");
		this.lastError = Objects.requireNonNull(errorType, "Error type is required.");
		clearClaim();
	}

	// Stops an old sender from changing an event that has since been claimed by another sender.
	private void requireCurrentClaim(UUID claimToken)
	{
		if (this.claimToken == null || !this.claimToken.equals(claimToken))
		{
			throw new IllegalStateException("The outbox event is no longer held by this claim.");
		}
	}

	// Clears the reservation after a send succeeds or a retry is scheduled.
	private void clearClaim()
	{
		this.claimToken = null;
		this.claimedUntil = null;
	}
}

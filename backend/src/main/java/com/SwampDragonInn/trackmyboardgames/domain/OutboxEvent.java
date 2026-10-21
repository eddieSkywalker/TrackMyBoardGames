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

// One durable event waiting to be published. The receiver will track processing separately.
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
	}

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

	public UUID getId()
	{
		return this.id;
	}

	public String getEventType()
	{
		return this.eventType;
	}

	public int getEventVersion()
	{
		return this.eventVersion;
	}

	public UUID getOwnerId()
	{
		return this.ownerId;
	}

	public UUID getAggregateId()
	{
		return this.aggregateId;
	}

	public String getPayload()
	{
		return this.payload;
	}

	public Instant getOccurredAt()
	{
		return this.occurredAt;
	}

	public Instant getPublishedAt()
	{
		return this.publishedAt;
	}

	public int getAttemptCount()
	{
		return this.attemptCount;
	}

	public Instant getNextAttemptAt()
	{
		return this.nextAttemptAt;
	}

	public UUID getClaimToken()
	{
		return this.claimToken;
	}

	public Instant getClaimedUntil()
	{
		return this.claimedUntil;
	}

	public String getLastError()
	{
		return this.lastError;
	}
}

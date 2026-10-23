package com.SwampDragonInn.trackmyboardgames.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// A receipt showing that the statistics receiver has already handled an event.
// This is separate from the outbox, which only tracks whether an event was sent to RabbitMQ.
@Entity
@Table(name = "statistics_processed_events")
public class StatisticsProcessedEvent
{

	@Id
	@Column(name = "event_id", updatable = false)
	private UUID eventId;

	@Column(name = "processed_at", nullable = false, updatable = false)
	private Instant processedAt;

	// JPA uses this constructor when reading a saved receipt.
	protected StatisticsProcessedEvent()
	{
	}

	// Returns the message ID that lets the statistics receiver recognize a repeat delivery.
	public UUID getEventId()
	{
		return this.eventId;
	}

	// Returns when the receiver recorded this event as handled.
	public Instant getProcessedAt()
	{
		return this.processedAt;
	}
}

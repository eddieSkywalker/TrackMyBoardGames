package com.SwampDragonInn.trackmyboardgames.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Checks for pending outbox events on a timer when RabbitMQ is enabled. This is scheduled polling, not a listener
// that wakes up as soon as a database row is created; each check attempts a bounded batch of events.
// Lets Spring create and manage this worker when RabbitMQ is enabled.
@Component
@ConditionalOnProperty(prefix = "app.messaging.rabbitmq", name = "enabled", havingValue = "true")
public class OutboxPollingWorker
{
	private final OutboxDispatcher dispatcher;
	private final int batchSize;

	public OutboxPollingWorker(
			OutboxDispatcher dispatcher,
			@Value("${app.messaging.rabbitmq.batch-size:10}") int batchSize)
	{
		if (batchSize < 1)
		{
			throw new IllegalArgumentException("RabbitMQ outbox batch size must be at least 1.");
		}

		this.dispatcher = dispatcher;
		this.batchSize = batchSize;
	}

	// Waits the configured number of milliseconds after each check finishes before starting another.
	// application.properties reads RABBITMQ_POLL_INTERVAL_MS; the default is 60000 (one minute).
	@Scheduled(fixedDelayString = "${app.messaging.rabbitmq.poll-interval-ms:60000}")
	public void publishNext()
	{
		this.dispatcher.publishBatch(this.batchSize);
	}
}

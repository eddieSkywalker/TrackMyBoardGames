package com.SwampDragonInn.trackmyboardgames.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Checks for pending outbox events on a timer when RabbitMQ is enabled. This is scheduled polling, not a listener
// that wakes up as soon as a database row is created; the dispatcher handles sending one event per check.
@Component
@ConditionalOnProperty(prefix = "app.messaging.rabbitmq", name = "enabled", havingValue = "true")
public class OutboxPollingWorker
{

	private final OutboxDispatcher dispatcher;

	// Uses the dispatcher to handle the database check and the actual send.
	public OutboxPollingWorker(OutboxDispatcher dispatcher)
	{
		this.dispatcher = dispatcher;
	}

	// Starts another check one minute after the previous check has finished.
	@Scheduled(fixedDelayString = "${app.messaging.rabbitmq.poll-interval-ms:60000}")
	public void publishNext()
	{
		this.dispatcher.publishNext();
	}
}

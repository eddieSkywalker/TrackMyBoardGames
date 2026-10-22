package com.SwampDragonInn.trackmyboardgames.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs only when the RabbitMQ connection is enabled; each tick sends at most one event.
@Component
@ConditionalOnProperty(prefix = "app.messaging.rabbitmq", name = "enabled", havingValue = "true")
public class OutboxPollingWorker
{

	private final OutboxDispatcher dispatcher;

	public OutboxPollingWorker(OutboxDispatcher dispatcher)
	{
		this.dispatcher = dispatcher;
	}

	@Scheduled(fixedDelayString = "${app.messaging.rabbitmq.poll-interval-ms:5000}")
	public void publishNext()
	{
		this.dispatcher.publishNext();
	}
}

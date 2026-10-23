package com.SwampDragonInn.trackmyboardgames.messaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.SwampDragonInn.trackmyboardgames.domain.OutboxEvent;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

// RabbitMQ connection and topology for publishing durable application events.
public class RabbitMqPublisher implements AutoCloseable
{

	public static final String EVENTS_EXCHANGE = "trackmyboardgames.events";
	public static final String STATISTICS_QUEUE = "trackmyboardgames.statistics";
	public static final String DEAD_LETTER_EXCHANGE = "trackmyboardgames.events.dlx";
	public static final String DEAD_LETTER_QUEUE = "trackmyboardgames.statistics.dlq";
	public static final String DEAD_LETTER_ROUTING_KEY = "statistics.failed";

	private final Connection connection;
	private final Channel channel;

	public RabbitMqPublisher(Connection connection, Channel channel)
	{
		this.connection = connection;
		this.channel = channel;
	}

	// Declare the exchanges and durable queues RabbitMQ uses to route and retain messages.
	public void declareTopology() throws IOException
	{
		this.channel.exchangeDeclare(EVENTS_EXCHANGE, "topic", true);
		this.channel.exchangeDeclare(DEAD_LETTER_EXCHANGE, "direct", true);
		this.channel.queueDeclare(DEAD_LETTER_QUEUE, true, false, false, Map.of());
		this.channel.queueBind(DEAD_LETTER_QUEUE, DEAD_LETTER_EXCHANGE, DEAD_LETTER_ROUTING_KEY);

		Map<String, Object> queueArguments = new HashMap<>();
		queueArguments.put("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE);
		queueArguments.put("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY);

		this.channel.queueDeclare(STATISTICS_QUEUE, true, false, false, queueArguments);
		this.channel.queueBind(STATISTICS_QUEUE, EVENTS_EXCHANGE, "#");
		this.channel.confirmSelect();
	}

	// Publish one persistent JSON message and wait for RabbitMQ's publisher confirmation.
	public synchronized void publish(OutboxEvent event) throws IOException, InterruptedException, TimeoutException
	{
		String routingKey = routingKey(event.getEventType());
		Map<String, Object> headers = new HashMap<>();
		headers.put("eventVersion", event.getEventVersion());
		headers.put("ownerId", event.getOwnerId().toString());
		headers.put("aggregateId", event.getAggregateId().toString());

		AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
				.contentType("application/json")
				.deliveryMode(2)
				.messageId(event.getId().toString())
				.type(event.getEventType())
				.timestamp(Date.from(event.getOccurredAt()))
				.headers(headers)
				.build();

		byte[] body = event.getPayload().getBytes(StandardCharsets.UTF_8);
		this.channel.basicPublish(EVENTS_EXCHANGE, routingKey, true, properties, body);
		this.channel.waitForConfirmsOrDie(5_000);
	}

	private String routingKey(String eventType)
	{
		return switch (eventType)
		{
			case "PlayRecorded" -> "play.recorded";
			case "PlayUpdated" -> "play.updated";
			case "PlayDeleted" -> "play.deleted";
			case "GameAdded" -> "game.added";
			case "GameUpdated" -> "game.updated";
			case "GameDeleted" -> "game.deleted";
			default -> throw new IllegalArgumentException("Unsupported outbox event type: " + eventType);
		};
	}

	@Override
	public void close() throws IOException, TimeoutException
	{
		try
		{
			if (this.channel.isOpen())
			{
				this.channel.close();
			}
		}
		finally
		{
			if (this.connection.isOpen())
			{
				this.connection.close();
			}
		}
	}
}

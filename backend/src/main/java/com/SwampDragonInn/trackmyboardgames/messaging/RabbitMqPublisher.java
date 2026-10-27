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

/**
 * Declares the RabbitMQ messaging topology and publishes durable application events.
 */
public class RabbitMqPublisher implements AutoCloseable
{

	public static final String EVENTS_EXCHANGE = "trackmyboardgames.events";
	public static final String STATISTICS_QUEUE = "trackmyboardgames.statistics";
	public static final String DEAD_LETTER_EXCHANGE = "trackmyboardgames.events.dlx";
	public static final String DEAD_LETTER_QUEUE = "trackmyboardgames.statistics.dlq";
	public static final String DEAD_LETTER_ROUTING_KEY = "statistics.failed";

	private final Connection connection;
	private final Channel channel;

	/**
	 * Creates a publisher using an open RabbitMQ connection and channel.
	 *
	 * @param connection connection to the RabbitMQ broker
	 * @param channel channel used to declare topology and publish messages
	 */
	public RabbitMqPublisher(Connection connection, Channel channel)
	{
		this.connection = connection;
		this.channel = channel;
	}

	/**
	 * Declares the exchanges, queues, and routing rules used by this publisher.
	 *
	 * @throws IOException if RabbitMQ cannot declare the messaging topology
	 */
	public void declareTopology() throws IOException
	{
		// Declare the durable topic exchange that routes events by routing key.
		this.channel.exchangeDeclare(EVENTS_EXCHANGE, "topic", true);

		// Failed consumer messages can be sent here and stored in a separate dead-letter queue.
		this.channel.exchangeDeclare(DEAD_LETTER_EXCHANGE, "direct", true);
		this.channel.queueDeclare(DEAD_LETTER_QUEUE, true, false, false, Map.of());
		this.channel.queueBind(DEAD_LETTER_QUEUE, DEAD_LETTER_EXCHANGE, DEAD_LETTER_ROUTING_KEY);

		// Tell RabbitMQ where to route messages rejected by the statistics queue's consumer.
		Map<String, Object> queueArguments = new HashMap<>();
		queueArguments.put("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE);
		queueArguments.put("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY);

		// The "#" topic pattern matches every routing key, so this queue receives all application events.
		this.channel.queueDeclare(STATISTICS_QUEUE, true, false, false, queueArguments);
		this.channel.queueBind(STATISTICS_QUEUE, EVENTS_EXCHANGE, "#");

		// Publisher confirms let publish() know RabbitMQ accepted each message before the outbox is updated.
		this.channel.confirmSelect();
	}

	/**
	 * Publishes one persistent JSON message and waits for RabbitMQ to confirm it.
	 *
	 * @param event outbox event to send
	 * @throws IOException if the message cannot be sent or RabbitMQ rejects it
	 * @throws InterruptedException if waiting for RabbitMQ's confirmation is interrupted
	 * @throws TimeoutException if RabbitMQ does not confirm the message before the timeout
	 */
	public synchronized void publish(OutboxEvent event) throws IOException, InterruptedException, TimeoutException
	{
		// The event type determines which binding key RabbitMQ uses to route this message.
		String routingKey = routingKey(event.getEventType());

		// Headers carry metadata separately from the event's JSON payload.
		Map<String, Object> headers = new HashMap<>();
		headers.put("eventVersion", event.getEventVersion());
		headers.put("ownerId", event.getOwnerId().toString());
		headers.put("aggregateId", event.getAggregateId().toString());

		// Properties help consumers identify, deserialize, and deduplicate the message.
		AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
				.contentType("application/json")
				.deliveryMode(2)
				.messageId(event.getId().toString())
				.type(event.getEventType())
				.timestamp(Date.from(event.getOccurredAt()))
				.headers(headers)
				.build();

		// Send the payload to the exchange; mandatory=true makes unroutable messages fail rather than disappear.
		byte[] body = event.getPayload().getBytes(StandardCharsets.UTF_8);
		this.channel.basicPublish(EVENTS_EXCHANGE, routingKey, true, properties, body);

		// Do not report success to the outbox dispatcher until RabbitMQ confirms this publish.
		this.channel.waitForConfirmsOrDie(5_000);
	}

	/**
	 * Converts a supported event type into the routing key used by the topic exchange.
	 *
	 * @param eventType name of the outbox event
	 * @return routing key that identifies the event category
	 * @throws IllegalArgumentException if the event type has no routing rule
	 */
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

	/**
	 * Closes the channel and then the connection to RabbitMQ.
	 *
	 * @throws IOException if closing the channel or connection fails
	 * @throws TimeoutException if RabbitMQ does not complete the close operation in time
	 */
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

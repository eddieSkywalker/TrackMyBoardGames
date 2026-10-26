package com.SwampDragonInn.trackmyboardgames.messaging;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;

import com.SwampDragonInn.trackmyboardgames.service.StatisticsRefreshService;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Receives game and play change messages from the RabbitMQ statistics queue.
 *
 * <p>For each message, it checks that the message is supported, asks the statistics service to recalculate
 * and save the owner's statistics, then acknowledges successful processing to RabbitMQ. Repeated deliveries
 * are safe because the service records which event IDs it has already handled.
 */
public class StatisticsMessageConsumer implements AutoCloseable
{

	private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsMessageConsumer.class);
	private static final Set<String> EVENT_TYPES = Set.of(
			"GameAdded", "GameUpdated", "GameDeleted", "PlayRecorded", "PlayUpdated", "PlayDeleted");

	private final Connection connection;
	private final Channel channel;
	private final StatisticsRefreshService statistics;
	private final JsonMapper jsonMapper;

	/**
	 * Creates the message receiver with its RabbitMQ connection and statistics dependencies.
	 *
	 * @param connection RabbitMQ connection used by this receiver
	 * @param channel RabbitMQ channel used to receive and acknowledge messages
	 * @param statistics service that recalculates and saves an owner's statistics summary
	 * @param jsonMapper reads the JSON event details
	 */
	public StatisticsMessageConsumer(Connection connection, Channel channel,
			StatisticsRefreshService statistics, JsonMapper jsonMapper)
	{
		this.connection = connection;
		this.channel = channel;
		this.statistics = statistics;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * Starts listening to the statistics queue with manual message acknowledgements.
	 *
	 * <p>RabbitMQ sends one unacknowledged message at a time. The receiver only acknowledges after the
	 * statistics transaction succeeds; merely receiving a message does not mean it was processed.
	 *
	 * @throws IOException if the consumer cannot be registered with RabbitMQ
	 */
	public void start() throws IOException
	{
		this.channel.basicQos(1);
		this.channel.basicConsume(RabbitMqPublisher.STATISTICS_QUEUE, false,
				(consumerTag, delivery) -> handleDelivery(delivery),
				consumerTag -> LOGGER.error("RabbitMQ cancelled the statistics consumer; check the queue and restart the receiver."));
	}

	/**
	 * Checks and processes one RabbitMQ delivery, then acknowledges, retries, or rejects it.
	 *
	 * <p>The statistics service returns only after its database transaction commits. Invalid messages are
	 * rejected without requeueing; temporary database failures can receive one immediate retry.
	 *
	 * @param delivery message and RabbitMQ delivery details
	 * @throws IOException if a RabbitMQ acknowledgement or rejection cannot be sent
	 */
	public void handleDelivery(Delivery delivery) throws IOException
	{
		long deliveryTag = delivery.getEnvelope().getDeliveryTag();
		MessageContext messageContext;
		try
		{
			messageContext = validate(delivery);
		}
		catch (IllegalArgumentException | JacksonException invalidMessage)
		{
			LOGGER.warn("Invalid statistics message at delivery {}; sending to the dead-letter queue ({})",
					deliveryTag, invalidMessage.getClass().getSimpleName());
			this.channel.basicReject(deliveryTag, false);
			return;
		}

		try
		{
			this.statistics.refresh(messageContext.eventId(), messageContext.ownerId());
		}
		catch (RuntimeException processingFailure)
		{
			// Retry a temporary database failure only on its first delivery. A redelivery might also
			// follow a connection loss, so this is a conservative retry policy, not an exact attempt counter.
			boolean retry = processingFailure instanceof TransientDataAccessException
					&& !delivery.getEnvelope().isRedeliver();
			LOGGER.error("Statistics event {} failed ({}); {}",
					messageContext.eventId(), processingFailure.getClass().getSimpleName(),
					retry ? "requeueing for one immediate retry" : "sending to the dead-letter queue");
			this.channel.basicNack(deliveryTag, false, retry);
			return;
		}

		this.channel.basicAck(deliveryTag, false);
	}

	/**
	 * Checks that a delivery has the supported event type, version, identifiers, and JSON shape.
	 *
	 * <p>The payload is checked as a JSON object, but its old details are not used to calculate totals.
	 * The statistics service reads the owner's current game and play records instead.
	 *
	 * @param delivery message and RabbitMQ delivery details to check
	 * @return the event ID used for duplicate detection and the owner ID whose statistics need recalculation
	 * @throws IllegalArgumentException if required metadata or payload structure is invalid
	 * @throws JacksonException if the payload is not valid JSON
	 */
	private MessageContext validate(Delivery delivery)
	{
		AMQP.BasicProperties properties = delivery.getProperties();

		// First make sure the message has JSON content and names an event this application understands.
		if (properties == null || !"application/json".equals(properties.getContentType())
				|| properties.getType() == null || !EVENT_TYPES.contains(properties.getType()))
		{
			throw new IllegalArgumentException("Unsupported statistics message.");
		}

		// Reject event versions this consumer does not know how to interpret.
		Map<String, Object> headers = properties.getHeaders();
		if (headers == null || !Integer.valueOf(1).equals(headers.get("eventVersion")))
		{
			throw new IllegalArgumentException("Unsupported statistics event version.");
		}

		// The event ID identifies this exact change; the owner ID identifies whose statistics to rebuild.
		UUID eventId = parseId(properties.getMessageId());
		UUID ownerId = parseId(headers.get("ownerId"));

		// Check that the changed game or play ID is present and correctly formatted, even though totals
		// are recalculated from current database records rather than from that one item.
		parseId(headers.get("aggregateId"));

		// Require a JSON object so malformed or unrelated payloads do not enter statistics processing.
		JsonNode payload = this.jsonMapper.readTree(delivery.getBody());
		if (payload == null || !payload.isObject())
		{
			throw new IllegalArgumentException("Statistics payload must be a JSON object.");
		}

		return new MessageContext(eventId, ownerId);
	}

	/**
	 * Converts a message identifier or header value into a UUID.
	 *
	 * <p>RabbitMQ may provide text headers as a LongString rather than a Java String, so the value is
	 * converted to text before parsing.
	 *
	 * @param value raw message property or header value
	 * @return parsed UUID
	 * @throws IllegalArgumentException if the value is missing or is not a full UUID
	 */
	private UUID parseId(Object value)
	{
		if (value == null)
		{
			throw new IllegalArgumentException("Message identifier is missing.");
		}
		String text = value.toString();
		UUID id = UUID.fromString(text);
		if (!id.toString().equalsIgnoreCase(text))
		{
			throw new IllegalArgumentException("Message identifier is not a full UUID.");
		}
		return id;
	}

	private record MessageContext(UUID eventId, UUID ownerId)
	{
	}

	/**
	 * Closes the channel and connection to RabbitMQ.
	 *
	 * <p>Messages not yet acknowledged are returned to RabbitMQ for later delivery; they are not marked
	 * as successfully processed.
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

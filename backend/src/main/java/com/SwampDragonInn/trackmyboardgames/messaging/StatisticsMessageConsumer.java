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

// Receives messages from the statistics queue; this is a RabbitMQ listener, not a database polling timer.
// It checks each message, asks the transactional service to recalculate the cards, then tells RabbitMQ
// the message is finished. Repeated deliveries are safe because the service remembers processed IDs.
public class StatisticsMessageConsumer implements AutoCloseable
{

	private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsMessageConsumer.class);
	private static final Set<String> EVENT_TYPES = Set.of(
			"GameAdded", "GameUpdated", "GameDeleted", "PlayRecorded", "PlayUpdated", "PlayDeleted");

	private final Connection connection;
	private final Channel channel;
	private final StatisticsRefreshService statistics;
	private final JsonMapper jsonMapper;

	// Uses a separate connection/channel so sending and receiving do not share channel state.
	public StatisticsMessageConsumer(Connection connection, Channel channel,
			StatisticsRefreshService statistics, JsonMapper jsonMapper)
	{
		this.connection = connection;
		this.channel = channel;
		this.statistics = statistics;
		this.jsonMapper = jsonMapper;
	}

	// Lets RabbitMQ deliver one unfinished message at a time. Automatic acknowledgements are disabled:
	// receiving a message is not the same as successfully saving the statistics.
	public void start() throws IOException
	{
		this.channel.basicQos(1);
		this.channel.basicConsume(RabbitMqPublisher.STATISTICS_QUEUE, false,
				(consumerTag, delivery) -> handleDelivery(delivery),
				consumerTag -> LOGGER.error("RabbitMQ cancelled the statistics consumer; check the queue and restart the receiver."));
	}

	// A successful service return means its database transaction has committed, including for a duplicate.
	// Acknowledgement failures are not treated as processing failures: RabbitMQ may redeliver, and the
	// committed event receipt will prevent another statistics update.
	public void handleDelivery(Delivery delivery) throws IOException
	{
		long deliveryTag = delivery.getEnvelope().getDeliveryTag();
		MessageIdentity identity;
		try
		{
			identity = validate(delivery);
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
			this.statistics.refresh(identity.eventId(), identity.ownerId());
		}
		catch (RuntimeException processingFailure)
		{
			// Retry a temporary database failure only on its first delivery. A redelivery might also
			// follow a connection loss, so this is a conservative retry policy, not an exact attempt counter.
			boolean retry = processingFailure instanceof TransientDataAccessException
					&& !delivery.getEnvelope().isRedeliver();
			LOGGER.error("Statistics event {} failed ({}); {}",
					identity.eventId(), processingFailure.getClass().getSimpleName(),
					retry ? "requeueing for one immediate retry" : "sending to the dead-letter queue");
			this.channel.basicNack(deliveryTag, false, retry);
			return;
		}

		this.channel.basicAck(deliveryTag, false);
	}

	// Only known version-1 messages may trigger a refresh. Their JSON details are checked as an object,
	// but calculations use current database records rather than applying an old message snapshot.
	private MessageIdentity validate(Delivery delivery)
	{
		AMQP.BasicProperties properties = delivery.getProperties();
		if (properties == null || !"application/json".equals(properties.getContentType())
				|| properties.getType() == null || !EVENT_TYPES.contains(properties.getType()))
		{
			throw new IllegalArgumentException("Unsupported statistics message.");
		}

		Map<String, Object> headers = properties.getHeaders();
		if (headers == null || !Integer.valueOf(1).equals(headers.get("eventVersion")))
		{
			throw new IllegalArgumentException("Unsupported statistics event version.");
		}

		UUID eventId = parseId(properties.getMessageId());
		UUID ownerId = parseId(headers.get("ownerId"));
		parseId(headers.get("aggregateId"));
		JsonNode payload = this.jsonMapper.readTree(delivery.getBody());
		if (payload == null || !payload.isObject())
		{
			throw new IllegalArgumentException("Statistics payload must be a JSON object.");
		}
		return new MessageIdentity(eventId, ownerId);
	}

	// AMQP can return text headers as LongString rather than String; both have a text representation.
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

	private record MessageIdentity(UUID eventId, UUID ownerId)
	{
	}

	// Closing the channel returns unfinished messages to RabbitMQ instead of marking them successful.
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

package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;

import com.SwampDragonInn.trackmyboardgames.messaging.RabbitMqPublisher;
import com.SwampDragonInn.trackmyboardgames.messaging.StatisticsMessageConsumer;
import com.SwampDragonInn.trackmyboardgames.service.StatisticsRefreshService;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.impl.LongStringHelper;

import tools.jackson.databind.json.JsonMapper;

// No broker is contacted: tests verify the receiver's decisions at the RabbitMQ channel boundary.
class StatisticsMessageConsumerTests
{

	private final Connection connection = mock(Connection.class);
	private final Channel channel = mock(Channel.class);
	private final StatisticsRefreshService statistics = mock(StatisticsRefreshService.class);
	private final UUID eventId = UUID.randomUUID();
	private final UUID ownerId = UUID.randomUUID();
	private StatisticsMessageConsumer consumer;

	@BeforeEach
	void createConsumer()
	{
		this.consumer = new StatisticsMessageConsumer(
				this.connection, this.channel, this.statistics, JsonMapper.builder().build());
	}

	@Test
	void startsPushListenerWithManualAcknowledgementsAndOneUnfinishedMessage() throws Exception
	{
		this.consumer.start();

		verify(this.channel).basicQos(1);
		verify(this.channel).basicConsume(eq(RabbitMqPublisher.STATISTICS_QUEUE), eq(false),
				any(DeliverCallback.class), any(CancelCallback.class));
	}

	@ParameterizedTest
	@ValueSource(strings = {"GameAdded", "GameUpdated", "GameDeleted", "PlayRecorded", "PlayUpdated", "PlayDeleted"})
	void acknowledgesSupportedEventsOnlyAfterServiceReturns(String eventType) throws Exception
	{
		when(this.statistics.refresh(this.eventId, this.ownerId)).thenReturn(true);

		this.consumer.handleDelivery(delivery(eventType, headers(), "{}", false));

		var order = inOrder(this.statistics, this.channel);
		order.verify(this.statistics).refresh(this.eventId, this.ownerId);
		order.verify(this.channel).basicAck(42L, false);
	}

	@Test
	void acknowledgesDuplicateWithoutTreatingItAsFailure() throws Exception
	{
		when(this.statistics.refresh(this.eventId, this.ownerId)).thenReturn(false);
		this.consumer.handleDelivery(delivery("PlayRecorded", headers(), "{}", true));

		verify(this.channel).basicAck(42L, false);
	}

	@Test
	void acceptsTextHeadersDecodedByRabbitMqAsLongStrings() throws Exception
	{
		Map<String, Object> headers = headers();
		headers.put("ownerId", LongStringHelper.asLongString(this.ownerId.toString()));
		this.consumer.handleDelivery(delivery("PlayRecorded", headers, "{}", false));

		verify(this.statistics).refresh(this.eventId, this.ownerId);
		verify(this.channel).basicAck(42L, false);
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "[]", "not-json"})
	void unsupportedTypeOrInvalidPayloadIsDeadLettered(String payload) throws Exception
	{
		String type = "{}".equals(payload) ? "UnknownEvent" : "PlayRecorded";
		this.consumer.handleDelivery(delivery(type, headers(), payload, false));

		verifyNoInteractions(this.statistics);
		verify(this.channel).basicReject(42L, false);
	}

	@Test
	void unsupportedVersionIsDeadLettered() throws Exception
	{
		Map<String, Object> headers = headers();
		headers.put("eventVersion", 2);
		this.consumer.handleDelivery(delivery("PlayRecorded", headers, "{}", false));

		verifyNoInteractions(this.statistics);
		verify(this.channel).basicReject(42L, false);
	}

	@Test
	void missingOwnerIsDeadLettered() throws Exception
	{
		Map<String, Object> headers = headers();
		headers.remove("ownerId");
		this.consumer.handleDelivery(delivery("PlayRecorded", headers, "{}", false));

		verifyNoInteractions(this.statistics);
		verify(this.channel).basicReject(42L, false);
	}

	@Test
	void temporaryDatabaseFailureRequeuesFirstDelivery() throws Exception
	{
		when(this.statistics.refresh(this.eventId, this.ownerId))
				.thenThrow(new CannotAcquireLockException("Private database failure details"));
		this.consumer.handleDelivery(delivery("PlayRecorded", headers(), "{}", false));

		verify(this.channel).basicNack(42L, false, true);
		verify(this.channel, never()).basicAck(42L, false);
	}

	@Test
	void temporaryFailureOnRedeliveryIsDeadLetteredInsteadOfLooping() throws Exception
	{
		when(this.statistics.refresh(this.eventId, this.ownerId))
				.thenThrow(new CannotAcquireLockException("Private database failure details"));
		this.consumer.handleDelivery(delivery("PlayRecorded", headers(), "{}", true));

		verify(this.channel).basicNack(42L, false, false);
		verify(this.channel, never()).basicAck(42L, false);
	}

	@Test
	void unexpectedProcessingFailureIsDeadLettered() throws Exception
	{
		when(this.statistics.refresh(this.eventId, this.ownerId))
				.thenThrow(new IllegalStateException("Statistics event owner does not exist."));
		this.consumer.handleDelivery(delivery("PlayRecorded", headers(), "{}", false));

		verify(this.channel).basicNack(42L, false, false);
		verify(this.channel, never()).basicAck(42L, false);
	}

	@Test
	void lostAcknowledgementIsNotMistakenForProcessingFailure() throws Exception
	{
		doThrow(new IOException("Connection lost")).when(this.channel).basicAck(42L, false);

		assertThatThrownBy(() -> this.consumer.handleDelivery(delivery("PlayRecorded", headers(), "{}", false)))
				.isInstanceOf(IOException.class);
		verify(this.channel, never()).basicNack(eq(42L), eq(false), anyBoolean());
	}

	@Test
	void closesChannelAndConnectionOnShutdown() throws Exception
	{
		when(this.channel.isOpen()).thenReturn(true);
		when(this.connection.isOpen()).thenReturn(true);
		this.consumer.close();

		verify(this.channel).close();
		verify(this.connection).close();
	}

	private Map<String, Object> headers()
	{
		return new HashMap<>(Map.of("eventVersion", 1, "ownerId", this.ownerId.toString(),
				"aggregateId", UUID.randomUUID().toString()));
	}

	private Delivery delivery(String type, Map<String, Object> headers, String payload, boolean redelivered)
	{
		AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
				.contentType("application/json")
				.messageId(this.eventId.toString())
				.type(type)
				.headers(headers)
				.build();
		return new Delivery(new Envelope(42L, redelivered, RabbitMqPublisher.EVENTS_EXCHANGE, "test"),
				properties, payload.getBytes(StandardCharsets.UTF_8));
	}
}

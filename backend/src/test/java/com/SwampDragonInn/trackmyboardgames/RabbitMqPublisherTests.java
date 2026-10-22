package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.SwampDragonInn.trackmyboardgames.domain.OutboxEvent;
import com.SwampDragonInn.trackmyboardgames.messaging.RabbitMqPublisher;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

class RabbitMqPublisherTests
{

	@Test
	void declaresDurableTopicExchangeStatisticsQueueAndDeadLetterRoute() throws Exception
	{
		Connection connection = mock(Connection.class);
		Channel channel = mock(Channel.class);
		RabbitMqPublisher publisher = new RabbitMqPublisher(connection, channel);

		publisher.declareTopology();

		verify(channel).exchangeDeclare(RabbitMqPublisher.EVENTS_EXCHANGE, "topic", true);
		verify(channel).exchangeDeclare(RabbitMqPublisher.DEAD_LETTER_EXCHANGE, "direct", true);
		verify(channel).queueDeclare(RabbitMqPublisher.DEAD_LETTER_QUEUE, true, false, false, Map.of());
		verify(channel).queueBind(RabbitMqPublisher.DEAD_LETTER_QUEUE,
				RabbitMqPublisher.DEAD_LETTER_EXCHANGE, RabbitMqPublisher.DEAD_LETTER_ROUTING_KEY);
		ArgumentCaptor<Map> queueArguments = ArgumentCaptor.forClass(Map.class);
		verify(channel).queueDeclare(
				eq(RabbitMqPublisher.STATISTICS_QUEUE), eq(true), eq(false), eq(false), queueArguments.capture());
		assertThat(queueArguments.getValue())
				.containsEntry("x-dead-letter-exchange", RabbitMqPublisher.DEAD_LETTER_EXCHANGE)
				.containsEntry("x-dead-letter-routing-key", RabbitMqPublisher.DEAD_LETTER_ROUTING_KEY);
		verify(channel).queueBind(RabbitMqPublisher.STATISTICS_QUEUE, RabbitMqPublisher.EVENTS_EXCHANGE, "#");
		verify(channel).confirmSelect();
	}

	@Test
	void publishesPersistentJsonMessageWithMetadataAndWaitsForConfirmation() throws Exception
	{
		Connection connection = mock(Connection.class);
		Channel channel = mock(Channel.class);
		RabbitMqPublisher publisher = new RabbitMqPublisher(connection, channel);
		UUID ownerId = UUID.randomUUID();
		UUID playId = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		Instant occurredAt = Instant.parse("2026-10-21T12:00:00Z");
		OutboxEvent event = mock(OutboxEvent.class);
		when(event.getId()).thenReturn(eventId);
		when(event.getEventType()).thenReturn("PlayRecorded");
		when(event.getEventVersion()).thenReturn(1);
		when(event.getOwnerId()).thenReturn(ownerId);
		when(event.getAggregateId()).thenReturn(playId);
		when(event.getPayload()).thenReturn("{\"playerCount\":4}");
		when(event.getOccurredAt()).thenReturn(occurredAt);

		publisher.publish(event);

		ArgumentCaptor<AMQP.BasicProperties> properties = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
		verify(channel).basicPublish(
				eq(RabbitMqPublisher.EVENTS_EXCHANGE),
				eq("play.recorded"),
				eq(true),
				properties.capture(),
				eq("{\"playerCount\":4}".getBytes(StandardCharsets.UTF_8)));
		assertThat(properties.getValue().getContentType()).isEqualTo("application/json");
		assertThat(properties.getValue().getDeliveryMode()).isEqualTo(2);
		assertThat(properties.getValue().getMessageId()).isEqualTo(eventId.toString());
		assertThat(properties.getValue().getType()).isEqualTo("PlayRecorded");
		assertThat(properties.getValue().getHeaders()).containsEntry("eventVersion", 1)
				.containsEntry("ownerId", ownerId.toString())
				.containsEntry("aggregateId", playId.toString());
		verify(channel).waitForConfirmsOrDie(5_000);
	}

	@Test
	void rejectsUnknownEventTypesInsteadOfSilentlyRoutingThem() throws Exception
	{
		Connection connection = mock(Connection.class);
		Channel channel = mock(Channel.class);
		RabbitMqPublisher publisher = new RabbitMqPublisher(connection, channel);
		OutboxEvent event = new OutboxEvent("UnknownEvent", 1, UUID.randomUUID(),
				UUID.randomUUID(), "{}", Instant.now());

		assertThatThrownBy(() -> publisher.publish(event))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Unsupported outbox event type");
		verify(channel, org.mockito.Mockito.never())
				.basicPublish(anyString(), anyString(), anyBoolean(), any(), any(byte[].class));
	}
}

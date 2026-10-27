package com.SwampDragonInn.trackmyboardgames.messaging;

import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.SwampDragonInn.trackmyboardgames.service.StatisticsRefreshService;

import tools.jackson.databind.json.JsonMapper;

@Configuration
// Keep local runs and tests independent of a hosted broker until RabbitMQ is explicitly enabled.
@ConditionalOnProperty(prefix = "app.messaging.rabbitmq", name = "enabled", havingValue = "true")
@EnableScheduling
public class RabbitMqConfiguration
{

	// Spring manages this publisher as one shared bean and closes it when the application stops.
	@Bean(destroyMethod = "close")
	RabbitMqPublisher rabbitMqPublisher(@Value("${app.messaging.rabbitmq.uri}") String connectionUri)
			throws IOException, TimeoutException, NoSuchAlgorithmException, KeyManagementException
	{
		ConnectionFactory factory = connectionFactory(connectionUri);

		// A connection is the TCP connection to RabbitMQ; the channel carries exchanges, queues, and messages.
		Connection connection = factory.newConnection("trackmyboardgames-outbox-publisher");
		try
		{
			Channel channel = connection.createChannel();

			// Declare our exchange and queues before either sending or receiving messages.
			RabbitMqPublisher publisher = new RabbitMqPublisher(connection, channel);
			publisher.declareTopology();
			return publisher;
		}
		catch (IOException | RuntimeException exception)
		{
			connection.close();
			throw exception;
		}
	}

	// The receiver is separately opt-in because enabling it starts processing queued messages immediately.
	// Wait for the publisher bean to declare the queue before trying to listen to it.
	@Bean(destroyMethod = "close")
	@DependsOn("rabbitMqPublisher")
	@ConditionalOnProperty(prefix = "app.messaging.rabbitmq", name = "consumer-enabled", havingValue = "true")
	StatisticsMessageConsumer statisticsMessageConsumer(
			@Value("${app.messaging.rabbitmq.uri}") String connectionUri,
			StatisticsRefreshService statistics, JsonMapper jsonMapper)
			throws IOException, TimeoutException, NoSuchAlgorithmException, KeyManagementException
	{
		Connection connection = connectionFactory(connectionUri)
				.newConnection("trackmyboardgames-statistics-consumer");
		try
		{
			StatisticsMessageConsumer consumer = new StatisticsMessageConsumer(
					connection, connection.createChannel(), statistics, jsonMapper);
			consumer.start();
			return consumer;
		}
		catch (IOException | RuntimeException exception)
		{
			connection.close();
			throw exception;
		}
	}

	// Both connections use the same private settings, TLS configuration, and automatic recovery.
	private ConnectionFactory connectionFactory(String connectionUri)
			throws NoSuchAlgorithmException, KeyManagementException
	{
		// Fail at startup with a clear message instead of silently running without the requested broker.
		if (connectionUri == null || connectionUri.isBlank())
		{
			throw new IllegalStateException(
					"RabbitMQ is enabled, but RABBITMQ_URI is not configured.");
		}

		// The CloudAMQP URI contains the host, TLS settings, virtual host, and credentials.
		// Keep the URI in environment configuration; never put it in source control.
		ConnectionFactory factory = new ConnectionFactory();
		factory.setUri(URI.create(connectionUri));

		// Bound connection startup time and detect broken network connections.
		factory.setConnectionTimeout(10_000);
		factory.setRequestedHeartbeat(30);

		// Reconnect the client automatically if the broker connection drops.
		factory.setAutomaticRecoveryEnabled(true);

		return factory;
	}
}

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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

@Configuration
// Keep local runs and tests independent of a hosted broker until RabbitMQ is explicitly enabled.
@ConditionalOnProperty(prefix = "app.messaging.rabbitmq", name = "enabled", havingValue = "true")
public class RabbitMqConfiguration
{

	// Spring manages this publisher as one shared bean and closes it when the application stops.
	@Bean(destroyMethod = "close")
	RabbitMqPublisher rabbitMqPublisher(@Value("${app.messaging.rabbitmq.uri}") String connectionUri)
			throws IOException, TimeoutException, NoSuchAlgorithmException, KeyManagementException
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

		// A connection is the TCP connection to RabbitMQ; the channel carries exchanges, queues, and messages.
		Connection connection = factory.newConnection("trackmyboardgames-outbox-publisher");
		try
		{
			Channel channel = connection.createChannel();

			// Declare our exchange and queues when connecting so the broker is ready for later publishing.
			RabbitMqPublisher publisher = new RabbitMqPublisher(connection, channel);
			publisher.declareTopology();
			return publisher;
		}
		catch (IOException | RuntimeException exception)
		{
			// If setup fails, do not leave an open connection behind.
			connection.close();
			throw exception;
		}
	}
}

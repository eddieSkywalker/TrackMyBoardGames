package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;
import com.SwampDragonInn.trackmyboardgames.messaging.StatisticsMessageConsumer;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsProcessedEventRepository;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsSummaryRepository;
import com.SwampDragonInn.trackmyboardgames.service.StatisticsRefreshService;
import com.SwampDragonInn.trackmyboardgames.service.UserProfileService;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

// Uses real PostgreSQL and the real transactional service, but a mocked broker channel.
@SpringBootTest
class StatisticsConsumerIntegrationTests
{

	@Autowired
	private StatisticsRefreshService statistics;

	@Autowired
	private UserProfileService profiles;

	@Autowired
	private StatisticsProcessedEventRepository receipts;

	@Autowired
	private StatisticsSummaryRepository summaries;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JsonMapper jsonMapper;

	private UUID ownerId;
	private UUID gameId;
	private final UUID eventId = UUID.randomUUID();

	@BeforeEach
	void createCollection()
	{
		new TransactionTemplate(this.transactionManager).executeWithoutResult(status ->
		{
			AppUser owner = this.profiles.findOrCreate("https://example.test/", "receiver-" + UUID.randomUUID());
			this.ownerId = owner.getId();
			BoardGame game = new BoardGame(owner, "Azul", "", null, null, null, null, null);
			this.entityManager.persist(game);
			this.gameId = game.getId();
		});
	}

	@AfterEach
	void removeTestRecords()
	{
		new TransactionTemplate(this.transactionManager).executeWithoutResult(status ->
		{
			this.entityManager.createQuery("delete from StatisticsProcessedEvent e where e.eventId = :id")
					.setParameter("id", this.eventId).executeUpdate();
			this.entityManager.createQuery("delete from AppUser u where u.id = :id")
					.setParameter("id", this.ownerId).executeUpdate();
		});
	}

	@Test
	void receiptAndStatisticsAreCommittedBeforeAckAndRepeatDeliveryDoesNotRecalculate() throws Exception
	{
		Channel channel = mock(Channel.class);
		StatisticsMessageConsumer consumer = new StatisticsMessageConsumer(
				mock(Connection.class), channel, this.statistics, this.jsonMapper);

		doAnswer(invocation ->
		{
			// These repository calls run after the service transaction has ended.
			assertThat(this.receipts.findById(this.eventId)).isPresent();
			assertThat(this.summaries.findById(this.ownerId).orElseThrow().getCollectionSize()).isEqualTo(1);
			return null;
		}).when(channel).basicAck(42L, false);

		AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
				.contentType("application/json")
				.type("GameAdded")
				.messageId(this.eventId.toString())
				.headers(Map.of("eventVersion", 1, "ownerId", this.ownerId.toString(),
						"aggregateId", this.gameId.toString()))
				.build();
		byte[] payload = ("{\"gameId\":\"" + this.gameId + "\",\"name\":\"Azul\",\"edition\":\"\"}")
				.getBytes(StandardCharsets.UTF_8);

		consumer.handleDelivery(new Delivery(new Envelope(42L, false, "test", "game.added"), properties, payload));
		Instant refreshedAt = this.summaries.findById(this.ownerId).orElseThrow().getRefreshedAt();
		consumer.handleDelivery(new Delivery(new Envelope(42L, true, "test", "game.added"), properties, payload));

		assertThat(this.summaries.findById(this.ownerId).orElseThrow().getRefreshedAt()).isEqualTo(refreshedAt);
		verify(channel, times(2)).basicAck(42L, false);
	}
}

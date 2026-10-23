package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.SwampDragonInn.trackmyboardgames.api.BoardGameRequest;
import com.SwampDragonInn.trackmyboardgames.api.BoardGameResponse;
import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;
import com.SwampDragonInn.trackmyboardgames.domain.GamePlay;
import com.SwampDragonInn.trackmyboardgames.domain.OutboxEvent;
import com.SwampDragonInn.trackmyboardgames.repository.OutboxEventRepository;
import com.SwampDragonInn.trackmyboardgames.service.BoardGameService;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Service calls commit independently so the tests can verify their persisted state and rollback behavior.
@SpringBootTest
class BoardGameOutboxTests
{

	private static final String ISSUER = "https://example.test/";

	@Autowired
	private BoardGameService service;

	@MockitoSpyBean
	private OutboxEventRepository outboxRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JsonMapper jsonMapper;

	private UUID ownerId;
	private String subject;

	@BeforeEach
	void createOwner()
	{
		this.subject = "game-outbox-test-" + UUID.randomUUID();
		new TransactionTemplate(this.transactionManager).executeWithoutResult(status ->
		{
			AppUser owner = new AppUser(ISSUER, this.subject);
			this.entityManager.persist(owner);
			this.ownerId = owner.getId();
		});
	}

	@AfterEach
	void removeTestRecords()
	{
		new TransactionTemplate(this.transactionManager).executeWithoutResult(status ->
		{
			this.entityManager.createQuery("delete from OutboxEvent e where e.ownerId = :ownerId")
					.setParameter("ownerId", this.ownerId).executeUpdate();
			// Database cascades remove this owner's games and their plays.
			this.entityManager.createQuery("delete from AppUser u where u.id = :ownerId")
					.setParameter("ownerId", this.ownerId).executeUpdate();
		});
	}

	@Test
	void createAndUpdateSaveVersionedEventsWithTheGameSnapshot()
	{
		BoardGameResponse created = this.service.create(ISSUER, this.subject, request("Wingspan", "Base"));

		assertThat(gameCount()).isEqualTo(1);
		assertEvent("GameAdded", created.id(), "Wingspan", "Base");

		BoardGameResponse updated = this.service.update(ISSUER, this.subject, created.id(),
				request("Wingspan", "Asia"));

		assertThat(gameCount()).isEqualTo(1);
		assertThat(eventsForOwner()).hasSize(2);
		assertEvent("GameUpdated", created.id(), "Wingspan", "Asia");
	}

	@Test
	void deletingGameSavesSnapshotAndCascadesItsPlays()
	{
		BoardGameResponse game = this.service.create(ISSUER, this.subject, request("Azul", null));
		new TransactionTemplate(this.transactionManager).executeWithoutResult(status ->
		{
			BoardGame storedGame = this.entityManager.find(BoardGame.class, game.id());
			this.entityManager.persist(new GamePlay(storedGame, LocalDate.of(2026, 1, 10), 45, 4, null));
		});

		this.service.delete(ISSUER, this.subject, game.id());

		assertThat(gameCount()).isZero();
		assertThat(playCount()).isZero();
		assertThat(eventsForOwner()).hasSize(2);
		assertEvent("GameDeleted", game.id(), "Azul", "");
	}

	@Test
	void failedCreateEventRollsBackTheGame()
	{
		failAfterSavingOutboxEvent();

		assertThatThrownBy(() -> this.service.create(ISSUER, this.subject, request("Azul", null)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Simulated outbox failure after SQL inserts");

		assertThat(gameCount()).isZero();
		assertThat(eventsForOwner()).isEmpty();
	}

	@Test
	void failedUpdateEventRollsBackTheGameChange()
	{
		BoardGameResponse created = this.service.create(ISSUER, this.subject, request("Azul", null));
		failAfterSavingOutboxEvent();

		assertThatThrownBy(() -> this.service.update(ISSUER, this.subject, created.id(),
				request("Azul: Summer Pavilion", null)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Simulated outbox failure after SQL inserts");

		assertThat(gameName(created.id())).isEqualTo("Azul");
		assertThat(eventsForOwner()).hasSize(1);
	}

	@Test
	void failedDeleteEventRollsBackTheGameDeletion()
	{
		BoardGameResponse created = this.service.create(ISSUER, this.subject, request("Azul", null));
		failAfterSavingOutboxEvent();

		assertThatThrownBy(() -> this.service.delete(ISSUER, this.subject, created.id()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Simulated outbox failure after SQL inserts");

		assertThat(gameCount()).isEqualTo(1);
		assertThat(gameName(created.id())).isEqualTo("Azul");
		assertThat(eventsForOwner()).hasSize(1);
	}

	private BoardGameRequest request(String name, String edition)
	{
		return new BoardGameRequest(name, edition, null, null, null, null, null, null);
	}

	private void assertEvent(String eventType, UUID gameId, String expectedName, String expectedEdition)
	{
		OutboxEvent event = eventsForOwner().stream()
				.filter(candidate -> candidate.getEventType().equals(eventType))
				.findFirst()
				.orElseThrow();
		assertThat(event.getEventVersion()).isEqualTo(1);
		assertThat(event.getAggregateId()).isEqualTo(gameId);
		assertThat(event.getPublishedAt()).isNull();

		JsonNode payload = this.jsonMapper.readTree(event.getPayload());
		assertThat(payload.get("gameId").asText()).isEqualTo(gameId.toString());
		assertThat(payload.get("name").asText()).isEqualTo(expectedName);
		assertThat(payload.get("edition").asText()).isEqualTo(expectedEdition);
	}

	private List<OutboxEvent> eventsForOwner()
	{
		return new TransactionTemplate(this.transactionManager).execute(status ->
				this.entityManager.createQuery("select e from OutboxEvent e where e.ownerId = :ownerId",
						OutboxEvent.class).setParameter("ownerId", this.ownerId).getResultList());
	}

	private long gameCount()
	{
		return new TransactionTemplate(this.transactionManager).execute(status ->
				this.entityManager.createQuery("select count(g) from BoardGame g where g.owner.id = :ownerId",
						Long.class).setParameter("ownerId", this.ownerId).getSingleResult());
	}

	private long playCount()
	{
		return new TransactionTemplate(this.transactionManager).execute(status ->
				this.entityManager.createQuery("select count(p) from GamePlay p where p.boardGame.owner.id = :ownerId",
						Long.class).setParameter("ownerId", this.ownerId).getSingleResult());
	}

	private String gameName(UUID gameId)
	{
		return new TransactionTemplate(this.transactionManager).execute(status ->
				this.entityManager.createQuery("select g.name from BoardGame g where g.id = :gameId",
						String.class).setParameter("gameId", gameId).getSingleResult());
	}

	private void failAfterSavingOutboxEvent()
	{
		doAnswer(invocation ->
		{
			OutboxEvent event = invocation.getArgument(0);
			this.entityManager.persist(event);
			this.entityManager.flush();
			throw new IllegalStateException("Simulated outbox failure after SQL inserts");
		}).when(this.outboxRepository).save(any(OutboxEvent.class));
	}
}

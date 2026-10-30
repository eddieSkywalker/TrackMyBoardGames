package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.SwampDragonInn.trackmyboardgames.api.GamePlayRequest;
import com.SwampDragonInn.trackmyboardgames.api.GamePlayResponse;
import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;
import com.SwampDragonInn.trackmyboardgames.domain.OutboxEvent;
import com.SwampDragonInn.trackmyboardgames.repository.OutboxEventRepository;
import com.SwampDragonInn.trackmyboardgames.service.GamePlayService;
import com.SwampDragonInn.trackmyboardgames.service.UserProfileService;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// No test-wide transaction: each service call must really commit or roll back before we inspect the DB.
@SpringBootTest
class GamePlayOutboxTests
{

	private static final String ISSUER = "https://example.test/";

	@Autowired
	private GamePlayService service;

	@Autowired
	private UserProfileService profiles;

	@MockitoSpyBean
	private OutboxEventRepository outboxRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JsonMapper jsonMapper;

	private UUID ownerId;
	private UUID gameId;
	private String subject;

	@BeforeEach
	void createOwnedGame()
	{
		this.subject = "outbox-test-" + UUID.randomUUID();
		new TransactionTemplate(this.transactionManager).executeWithoutResult(status ->
		{
			AppUser owner = this.profiles.findOrCreate(ISSUER, this.subject);
			BoardGame game = new BoardGame(owner, "Outbox test game", "", null, null, null, null, null);
			this.entityManager.persist(game);
			this.ownerId = owner.getId();
			this.gameId = game.getId();
		});
	}

	@AfterEach
	void removeTestRecords()
	{
		new TransactionTemplate(this.transactionManager).executeWithoutResult(status ->
		{
			this.entityManager.createQuery("delete from OutboxEvent e where e.ownerId = :ownerId")
					.setParameter("ownerId", this.ownerId).executeUpdate();
			// Source-table cascades remove this owner's game and plays.
			this.entityManager.createQuery("delete from AppUser u where u.id = :ownerId")
					.setParameter("ownerId", this.ownerId).executeUpdate();
		});
	}

	@Test
	void commitsOnePlayAndOnePendingEventWithTheExpectedSnapshot()
	{
		GamePlayResponse play = this.service.create(ISSUER, this.subject, this.gameId,
				request(45, 4));
		List<OutboxEvent> events = eventsForOwner();
		assertThat(events).hasSize(1);
		OutboxEvent event = events.getFirst();

		assertThat(playCount()).isEqualTo(1);
		assertThat(event.getEventType()).isEqualTo("PlayRecorded");
		assertThat(event.getEventVersion()).isEqualTo(1);
		assertThat(event.getAggregateId()).isEqualTo(play.id());
		assertThat(event.getPublishedAt()).isNull();
		assertThat(event.getAttemptCount()).isZero();
		assertThat(event.getNextAttemptAt()).isEqualTo(event.getOccurredAt());
		// PostgreSQL rounds Instants to microseconds; compare the two persisted timestamps.
		Instant persistedCreationTime = new TransactionTemplate(this.transactionManager).execute(status ->
				this.entityManager.createQuery("select p.createdAt from GamePlay p where p.id = :id",
						Instant.class).setParameter("id", play.id()).getSingleResult());
		assertThat(event.getOccurredAt()).isEqualTo(persistedCreationTime);

		JsonNode payload = this.jsonMapper.readTree(event.getPayload());
		assertThat(payload.size()).isEqualTo(5);
		assertThat(payload.get("playId").asText()).isEqualTo(play.id().toString());
		assertThat(payload.get("gameId").asText()).isEqualTo(this.gameId.toString());
		assertThat(payload.get("playedOn").asText()).isEqualTo("2026-01-10");
		assertThat(payload.get("durationMinutes").asInt()).isEqualTo(45);
		assertThat(payload.get("playerCount").asInt()).isEqualTo(4);
	}

	@Test
	void preservesMissingOptionalValues()
	{
		this.service.create(ISSUER, this.subject, this.gameId, request(null, null));
		JsonNode payload = this.jsonMapper.readTree(eventsForOwner().getFirst().getPayload());

		assertThat(payload.get("durationMinutes").isNull()).isTrue();
		assertThat(payload.get("playerCount").isNull()).isTrue();
	}

	@Test
	void updateAndDeleteEachSaveAnEventWithTheAffectedPlaySnapshot()
	{
		GamePlayResponse created = this.service.create(ISSUER, this.subject, this.gameId, request(45, 3));
		GamePlayResponse updated = this.service.update(ISSUER, this.subject, this.gameId, created.id(),
				new GamePlayRequest(LocalDate.of(2026, 1, 11), 60, 4, "Updated private notes"));

		assertThat(playCount()).isEqualTo(1);
		assertThat(eventsForOwner()).hasSize(2);
		JsonNode updatedPayload = payloadForEvent("PlayUpdated");
		assertThat(updatedPayload.get("playId").asText()).isEqualTo(created.id().toString());
		assertThat(updatedPayload.get("gameId").asText()).isEqualTo(this.gameId.toString());
		assertThat(updatedPayload.get("playedOn").asText()).isEqualTo("2026-01-11");
		assertThat(updatedPayload.get("durationMinutes").asInt()).isEqualTo(60);
		assertThat(updatedPayload.get("playerCount").asInt()).isEqualTo(4);
		assertThat(updatedPayload.has("notes")).isFalse();

		this.service.delete(ISSUER, this.subject, this.gameId, updated.id());

		assertThat(playCount()).isZero();
		assertThat(eventsForOwner()).hasSize(3);
		JsonNode deletedPayload = payloadForEvent("PlayDeleted");
		assertThat(deletedPayload.get("playId").asText()).isEqualTo(created.id().toString());
		assertThat(deletedPayload.get("playedOn").asText()).isEqualTo("2026-01-11");
		assertThat(deletedPayload.get("playerCount").asInt()).isEqualTo(4);
	}

	@Test
	void failedUpdateEventRollsBackThePlayChange()
	{
		GamePlayResponse created = this.service.create(ISSUER, this.subject, this.gameId, request(45, 3));
		failAfterSavingOutboxEvent();

		assertThatThrownBy(() -> this.service.update(ISSUER, this.subject, this.gameId, created.id(),
				new GamePlayRequest(LocalDate.of(2026, 1, 11), 60, 4, null)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Simulated outbox failure after SQL inserts");

		assertThat(playCount()).isEqualTo(1);
		assertThat(playDate(created.id())).isEqualTo(LocalDate.of(2026, 1, 10));
		assertThat(eventsForOwner()).hasSize(1);
	}

	@Test
	void failedDeleteEventRollsBackThePlayDeletion()
	{
		GamePlayResponse created = this.service.create(ISSUER, this.subject, this.gameId, request(45, 3));
		failAfterSavingOutboxEvent();

		assertThatThrownBy(() -> this.service.delete(ISSUER, this.subject, this.gameId, created.id()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Simulated outbox failure after SQL inserts");

		assertThat(playCount()).isEqualTo(1);
		assertThat(playDate(created.id())).isEqualTo(LocalDate.of(2026, 1, 10));
		assertThat(eventsForOwner()).hasSize(1);
	}

	@Test
	void outboxFailureRollsBackEvenAfterBothInsertsHaveBeenFlushed()
	{
		failAfterSavingOutboxEvent();

		assertThatThrownBy(() -> this.service.create(ISSUER, this.subject, this.gameId, request(45, 4)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Simulated outbox failure after SQL inserts");

		assertThat(playCount()).isZero();
		assertThat(eventsForOwner()).isEmpty();
	}

	@Test
	void databaseRejectionOfThePlayAlsoRollsBackTheEvent()
	{
		// Bypass HTTP validation to exercise a real database failure at transaction commit.
		assertThatThrownBy(() -> this.service.create(ISSUER, this.subject, this.gameId, request(0, 4)))
				.isInstanceOf(RuntimeException.class)
				.hasStackTraceContaining("ck_game_plays_duration");

		assertThat(playCount()).isZero();
		assertThat(eventsForOwner()).isEmpty();
	}

	@Test
	void rejectedDateAndUnownedGameDoNotCreateEvents()
	{
		assertThatThrownBy(() -> this.service.create(ISSUER, this.subject, this.gameId,
				new GamePlayRequest(LocalDate.now().plusDays(30), null, null, null)))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
		assertThatThrownBy(() -> this.service.create(ISSUER, this.subject, UUID.randomUUID(), request(45, 4)))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
		assertThatThrownBy(() -> this.service.create(ISSUER, "other-" + UUID.randomUUID(),
				this.gameId, request(45, 4)))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

		assertThat(playCount()).isZero();
		assertThat(eventsForOwner()).isEmpty();
	}

	private GamePlayRequest request(Integer durationMinutes, Integer playerCount)
	{
		return new GamePlayRequest(LocalDate.of(2026, 1, 10), durationMinutes, playerCount,
				"Private notes must not enter the event payload");
	}

	private List<OutboxEvent> eventsForOwner()
	{
		return new TransactionTemplate(this.transactionManager).execute(status ->
				this.entityManager.createQuery("select e from OutboxEvent e where e.ownerId = :ownerId",
						OutboxEvent.class).setParameter("ownerId", this.ownerId).getResultList());
	}

	private JsonNode payloadForEvent(String eventType)
	{
		OutboxEvent event = eventsForOwner().stream()
				.filter(candidate -> candidate.getEventType().equals(eventType))
				.findFirst()
				.orElseThrow();
		return this.jsonMapper.readTree(event.getPayload());
	}

	private LocalDate playDate(UUID playId)
	{
		return new TransactionTemplate(this.transactionManager).execute(status ->
				this.entityManager.createQuery("select p.playedOn from GamePlay p where p.id = :playId",
						LocalDate.class).setParameter("playId", playId).getSingleResult());
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

	private long playCount()
	{
		return new TransactionTemplate(this.transactionManager).execute(status ->
				this.entityManager.createQuery("select count(p) from GamePlay p where p.boardGame.id = :gameId",
						Long.class).setParameter("gameId", this.gameId).getSingleResult());
	}
}

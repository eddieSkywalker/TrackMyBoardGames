package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.SwampDragonInn.trackmyboardgames.domain.OutboxEvent;
import com.SwampDragonInn.trackmyboardgames.repository.OutboxEventRepository;

import jakarta.persistence.EntityManager;

// Uses real PostgreSQL/Flyway so JSONB and database constraints are tested, not approximated.
@SpringBootTest
@Transactional
class OutboxEventPersistenceTests
{

	@Autowired
	private OutboxEventRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private OutboxEvent newEvent(String type, String payload)
	{
		return new OutboxEvent(type, 1, UUID.randomUUID(), UUID.randomUUID(),
				payload, Instant.parse("2026-10-20T12:00:00Z"));
	}

	@Test
	void savesAndReloadsAnImmediatelyPendingEvent()
	{
		OutboxEvent event = newEvent("PlayRecorded", "{\"playerCount\":4}");
		this.repository.saveAndFlush(event);
		this.entityManager.clear();

		OutboxEvent saved = this.repository.findById(event.getId()).orElseThrow();

		assertThat(saved.getEventType()).isEqualTo("PlayRecorded");
		assertThat(saved.getEventVersion()).isEqualTo(1);
		assertThat(saved.getOwnerId()).isEqualTo(event.getOwnerId());
		assertThat(saved.getAggregateId()).isEqualTo(event.getAggregateId());
		assertThat(saved.getPayload()).contains("\"playerCount\"").contains("4");
		assertThat(saved.getOccurredAt()).isEqualTo(event.getOccurredAt());
		assertThat(saved.getNextAttemptAt()).isEqualTo(event.getOccurredAt());
		assertThat(saved.getPublishedAt()).isNull();
		assertThat(saved.getAttemptCount()).isZero();
		assertThat(saved.getClaimToken()).isNull();
		assertThat(saved.getClaimedUntil()).isNull();
		assertThat(saved.getLastError()).isNull();

		// Verify a JSON object was stored, not a JSON string containing escaped JSON.
		Object players = this.entityManager.createNativeQuery(
				"select payload ->> 'playerCount' from outbox_events where id = :id", String.class)
				.setParameter("id", saved.getId()).getSingleResult();
		assertThat(players).isEqualTo("4");
	}

	@Test
	void supportsMultipleEventTypesWithoutRequiringSourceRecords()
	{
		// Random identifiers deliberately have no corresponding user/game/play rows.
		OutboxEvent added = this.repository.saveAndFlush(newEvent("GameAdded", "{}"));
		OutboxEvent deleted = this.repository.saveAndFlush(newEvent("PlayDeleted", "{}"));
		this.entityManager.clear();

		assertThat(this.repository.findById(added.getId())).isPresent();
		assertThat(this.repository.findById(deleted.getId())).isPresent();
	}

	@Test
	void rejectsPayloadsThatAreNotJsonObjects()
	{
		assertThatThrownBy(() -> this.repository.saveAndFlush(newEvent("PlayRecorded", "[]")))
				.isInstanceOf(RuntimeException.class)
				.hasStackTraceContaining("ck_outbox_events_payload");
	}

	@Test
	void validatesEventTypeAndVersionBeforePersistence()
	{
		assertThatThrownBy(() -> newEvent(" ", "{}")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> newEvent("x".repeat(101), "{}")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OutboxEvent("PlayRecorded", 0, UUID.randomUUID(),
				UUID.randomUUID(), "{}", Instant.now())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rollingBackATransactionDoesNotLeaveAnEvent()
	{
		OutboxEvent event = newEvent("PlayRecorded", "{}");
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		transaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

		assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
		{
			this.repository.saveAndFlush(event);
			throw new IllegalStateException("Simulated failure after saving the event");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(this.repository.findById(event.getId())).isEmpty();
	}
}

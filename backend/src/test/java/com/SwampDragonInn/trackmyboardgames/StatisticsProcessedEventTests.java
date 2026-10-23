package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.SwampDragonInn.trackmyboardgames.domain.StatisticsProcessedEvent;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsProcessedEventRepository;

import jakarta.persistence.EntityManager;

// Uses PostgreSQL to check duplicate protection, transaction rollback, and two receivers racing for one event.
@SpringBootTest
class StatisticsProcessedEventTests
{

	@Autowired
	private StatisticsProcessedEventRepository repository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private EntityManager entityManager;

	private final List<UUID> testEventIds = new ArrayList<>();

	@AfterEach
	void removeTestReceipts()
	{
		transaction().executeWithoutResult(status -> this.entityManager.createQuery(
				"delete from StatisticsProcessedEvent event where event.eventId in :ids")
				.setParameter("ids", this.testEventIds)
				.executeUpdate());
	}

	@Test
	void savesReceiptWithoutRequiringAnOutboxOrSourceRecord()
	{
		UUID eventId = newEventId();
		Instant processedAt = Instant.parse("2026-10-22T18:00:00Z");

		Integer inserted = transaction().execute(status -> this.repository.recordIfNew(eventId, processedAt));
		assertThat(inserted).isEqualTo(1);

		StatisticsProcessedEvent receipt = this.repository.findById(eventId).orElseThrow();
		assertThat(receipt.getEventId()).isEqualTo(eventId);
		assertThat(receipt.getProcessedAt()).isEqualTo(processedAt);
	}

	@Test
	void repeatDeliveryDoesNotReplaceTheOriginalReceipt()
	{
		UUID eventId = newEventId();
		Instant firstProcessedAt = Instant.parse("2026-10-22T18:00:00Z");
		transaction().executeWithoutResult(status -> this.repository.recordIfNew(eventId, firstProcessedAt));

		Integer inserted = transaction().execute(status ->
				this.repository.recordIfNew(eventId, firstProcessedAt.plusSeconds(60)));
		assertThat(inserted).isZero();
		assertThat(this.repository.findById(eventId).orElseThrow().getProcessedAt()).isEqualTo(firstProcessedAt);
	}

	@Test
	void differentEventsCanBothBeRecorded()
	{
		UUID firstId = newEventId();
		UUID secondId = newEventId();

		transaction().executeWithoutResult(status ->
		{
			assertThat(this.repository.recordIfNew(firstId, Instant.now())).isEqualTo(1);
			assertThat(this.repository.recordIfNew(secondId, Instant.now())).isEqualTo(1);
		});

		assertThat(this.repository.findById(firstId)).isPresent();
		assertThat(this.repository.findById(secondId)).isPresent();
	}

	@Test
	void failedProcessingRollsBackReceiptSoDeliveryCanBeRetried()
	{
		UUID eventId = newEventId();

		assertThatThrownBy(() -> transaction().executeWithoutResult(status ->
		{
			this.repository.recordIfNew(eventId, Instant.now());
			throw new IllegalStateException("Simulated statistics update failure");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(this.repository.findById(eventId)).isEmpty();
		Integer inserted = transaction().execute(status -> this.repository.recordIfNew(eventId, Instant.now()));
		assertThat(inserted).isEqualTo(1);
	}

	@Test
	void refusesToSaveReceiptOutsideTheProcessingTransaction()
	{
		UUID eventId = newEventId();

		assertThatThrownBy(() -> this.repository.recordIfNew(eventId, Instant.now()))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(this.repository.findById(eventId)).isEmpty();
	}

	@Test
	void simultaneousDeliveriesRecordOnlyOneReceipt() throws Exception
	{
		UUID eventId = newEventId();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try (var executor = Executors.newFixedThreadPool(2))
		{
			Callable<Integer> delivery = () ->
			{
				ready.countDown();
				if (!start.await(10, TimeUnit.SECONDS))
				{
					throw new IllegalStateException("Timed out waiting to start concurrent deliveries");
				}
				return transaction().execute(status -> this.repository.recordIfNew(eventId, Instant.now()));
			};

			var first = executor.submit(delivery);
			var second = executor.submit(delivery);
			try
			{
				assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			}
			finally
			{
				start.countDown();
			}

			assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(1, 0);
		}

		assertThat(this.repository.findById(eventId)).isPresent();
	}

	private UUID newEventId()
	{
		UUID eventId = UUID.randomUUID();
		this.testEventIds.add(eventId);
		return eventId;
	}

	private TransactionTemplate transaction()
	{
		return new TransactionTemplate(this.transactionManager);
	}
}

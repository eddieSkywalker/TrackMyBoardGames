package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.SwampDragonInn.trackmyboardgames.domain.OutboxEvent;
import com.SwampDragonInn.trackmyboardgames.messaging.OutboxClaimService;
import com.SwampDragonInn.trackmyboardgames.messaging.OutboxDispatcher;
import com.SwampDragonInn.trackmyboardgames.messaging.RabbitMqPublisher;
import com.SwampDragonInn.trackmyboardgames.repository.OutboxEventRepository;

// Uses PostgreSQL to verify real event claims, retry state, and publication state transitions.
@SpringBootTest
class OutboxDispatcherTests
{

	@Autowired
	private OutboxEventRepository repository;

	@Autowired
	private OutboxClaimService claimService;

	@MockitoBean
	private RabbitMqPublisher publisher;

	private final List<UUID> testEventIds = new ArrayList<>();

	@AfterEach
	void removeTestEvents()
	{
		this.repository.deleteAllById(this.testEventIds);
	}

	@Test
	void confirmedPublishMarksEventPublishedAndReleasesItsClaim() throws Exception
	{
		OutboxEvent event = createEvent(Instant.now().minusSeconds(60));
		OutboxDispatcher dispatcher = new OutboxDispatcher(this.claimService, this.publisher);

		assertThat(dispatcher.publishNext()).isTrue();

		OutboxEvent published = this.repository.findById(event.getId()).orElseThrow();
		assertThat(published.getPublishedAt()).isNotNull();
		assertThat(published.getAttemptCount()).isEqualTo(1);
		assertThat(published.getClaimToken()).isNull();
		assertThat(published.getClaimedUntil()).isNull();
		verify(this.publisher).publish(any(OutboxEvent.class));
	}

	@Test
	void publishFailureReleasesClaimAndSchedulesBoundedRetry() throws Exception
	{
		OutboxEvent event = createEvent(Instant.now().minusSeconds(60));
		Instant startedAt = Instant.now();
		doThrow(new IOException("The broker message is intentionally not logged or persisted"))
				.when(this.publisher).publish(any(OutboxEvent.class));
		OutboxDispatcher dispatcher = new OutboxDispatcher(this.claimService, this.publisher);

		assertThat(dispatcher.publishNext()).isTrue();

		OutboxEvent retry = this.repository.findById(event.getId()).orElseThrow();
		assertThat(retry.getPublishedAt()).isNull();
		assertThat(retry.getAttemptCount()).isEqualTo(1);
		assertThat(retry.getLastError()).isEqualTo("IOException");
		assertThat(retry.getNextAttemptAt()).isAfter(startedAt);
		assertThat(retry.getClaimToken()).isNull();
		assertThat(retry.getClaimedUntil()).isNull();
	}

	@Test
	void publishBatchAttemptsMultipleDueEventsAndStopsAtConfiguredLimit() throws Exception
	{
		OutboxEvent firstEvent = createEvent(Instant.now().minusSeconds(60));
		OutboxEvent secondEvent = createEvent(Instant.now().minusSeconds(30));
		createEvent(Instant.now().minusSeconds(10));
		OutboxDispatcher dispatcher = new OutboxDispatcher(this.claimService, this.publisher);

		assertThat(dispatcher.publishBatch(2)).isEqualTo(2);

		assertThat(this.repository.findById(firstEvent.getId()).orElseThrow().getPublishedAt()).isNotNull();
		assertThat(this.repository.findById(secondEvent.getId()).orElseThrow().getPublishedAt()).isNotNull();
		assertThat(this.repository.findAllById(this.testEventIds))
				.filteredOn(event -> event.getPublishedAt() != null)
				.hasSize(2);
		verify(this.publisher, org.mockito.Mockito.times(2)).publish(any(OutboxEvent.class));
	}

	@Test
	void publishBatchStopsWhenThereAreNoMoreDueEvents()
	{
		createEvent(Instant.now().minusSeconds(60));
		OutboxDispatcher dispatcher = new OutboxDispatcher(this.claimService, this.publisher);

		assertThat(dispatcher.publishBatch(10)).isEqualTo(1);
	}

	@Test
	void activeClaimPreventsAnotherWorkerFromTakingTheSameEvent()
	{
		OutboxEvent event = createEvent(Instant.now().minusSeconds(60));
		Instant now = Instant.now();

		assertThat(this.claimService.claimNext(now)).isPresent();
		assertThat(this.claimService.claimNext(now)).isEmpty();

		OutboxEvent claimed = this.repository.findById(event.getId()).orElseThrow();
		assertThat(claimed.getClaimToken()).isNotNull();
		assertThat(claimed.getClaimedUntil()).isAfter(now);
		assertThat(claimed.getAttemptCount()).isEqualTo(1);
	}

	@Test
	void expiredClaimCanBeReclaimedAndOldClaimCannotMarkItPublished()
	{
		OutboxEvent event = createEvent(Instant.now().minusSeconds(60));
		Instant firstClaimTime = Instant.now();
		OutboxEvent firstClaim = this.claimService.claimNext(firstClaimTime).orElseThrow();
		OutboxEvent secondClaim = this.claimService.claimNext(firstClaimTime.plusSeconds(31)).orElseThrow();

		assertThat(secondClaim.getClaimToken()).isNotEqualTo(firstClaim.getClaimToken());
		assertThat(secondClaim.getAttemptCount()).isEqualTo(2);
		assertThatThrownBy(() -> this.claimService.markPublished(
				event.getId(), firstClaim.getClaimToken(), Instant.now()))
				.isInstanceOf(IllegalStateException.class);
		assertThat(this.repository.findById(event.getId()).orElseThrow().getPublishedAt()).isNull();

		this.claimService.markPublished(event.getId(), secondClaim.getClaimToken(), Instant.now());
		assertThat(this.repository.findById(event.getId()).orElseThrow().getPublishedAt()).isNotNull();
	}

	@Test
	void futureRetryTimeIsNotEligibleYet()
	{
		OutboxEvent event = createEvent(Instant.now().plusSeconds(60));
		OutboxDispatcher dispatcher = new OutboxDispatcher(this.claimService, this.publisher);

		assertThat(dispatcher.publishNext()).isFalse();
		assertThat(this.repository.findById(event.getId()).orElseThrow().getAttemptCount()).isZero();
	}

	@Test
	void fifthFailedPublishStopsFurtherClaimsWithoutBlockingOtherEvents() throws Exception
	{
		OutboxEvent event = createEventWithFourAttempts();
		doThrow(new IOException("Simulated broker failure"))
				.when(this.publisher).publish(any(OutboxEvent.class));
		OutboxDispatcher dispatcher = new OutboxDispatcher(this.claimService, this.publisher);

		assertThat(dispatcher.publishNext()).isTrue();

		OutboxEvent exhausted = this.repository.findById(event.getId()).orElseThrow();
		assertThat(exhausted.getAttemptCount()).isEqualTo(5);
		assertThat(exhausted.getPublishedAt()).isNull();
		assertThat(exhausted.getLastError()).isEqualTo("IOException");
		assertThat(exhausted.getClaimToken()).isNull();
		assertThat(exhausted.getClaimedUntil()).isNull();
		assertThat(this.claimService.claimNext(Instant.now().plusSeconds(3600))).isEmpty();

		OutboxEvent anotherEvent = createEvent(Instant.now().minusSeconds(10));
		assertThat(this.claimService.claimNext(Instant.now())).get()
				.extracting(OutboxEvent::getId).isEqualTo(anotherEvent.getId());
		verify(this.publisher).publish(any(OutboxEvent.class));
	}

	@Test
	void fifthAttemptCanStillPublishSuccessfully()
	{
		OutboxEvent event = createEventWithFourAttempts();
		OutboxDispatcher dispatcher = new OutboxDispatcher(this.claimService, this.publisher);

		assertThat(dispatcher.publishNext()).isTrue();

		OutboxEvent published = this.repository.findById(event.getId()).orElseThrow();
		assertThat(published.getAttemptCount()).isEqualTo(5);
		assertThat(published.getPublishedAt()).isNotNull();
	}

	@Test
	void expiredFifthClaimCannotBeClaimedForASixthAttempt()
	{
		OutboxEvent event = createEventWithFourAttempts();
		Instant now = Instant.now();

		assertThat(this.claimService.claimNext(now)).get()
				.extracting(OutboxEvent::getId).isEqualTo(event.getId());
		assertThat(this.claimService.claimNext(now.plusSeconds(31))).isEmpty();
		assertThat(this.repository.findById(event.getId()).orElseThrow().getAttemptCount()).isEqualTo(5);
	}

	private OutboxEvent createEventWithFourAttempts()
	{
		OutboxEvent event = createEvent(Instant.now().minusSeconds(60));
		for (int attempt = 0; attempt < 4; attempt++)
		{
			UUID claimToken = UUID.randomUUID();
			event.claim(claimToken, Instant.now().plusSeconds(30));
			event.scheduleRetry(claimToken, Instant.now().minusSeconds(1), "IOException");
		}
		return this.repository.saveAndFlush(event);
	}

	private OutboxEvent createEvent(Instant occurredAt)
	{
		OutboxEvent event = this.repository.saveAndFlush(new OutboxEvent(
				"PlayRecorded", 1, UUID.randomUUID(), UUID.randomUUID(), "{\"playId\":\"test\"}", occurredAt));
		this.testEventIds.add(event.getId());
		return event;
	}
}

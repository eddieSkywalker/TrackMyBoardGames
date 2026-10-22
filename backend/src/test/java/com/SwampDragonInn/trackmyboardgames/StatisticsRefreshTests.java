package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
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

import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;
import com.SwampDragonInn.trackmyboardgames.domain.GamePlay;
import com.SwampDragonInn.trackmyboardgames.domain.StatisticsSummary;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsProcessedEventRepository;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsSummaryRepository;
import com.SwampDragonInn.trackmyboardgames.service.StatisticsRefreshService;

import jakarta.persistence.EntityManager;

// Exercises actual commits so the tests can verify saved cards, repeat events, and failed-refresh rollback.
@SpringBootTest
class StatisticsRefreshTests
{

	@Autowired
	private StatisticsRefreshService service;

	@MockitoSpyBean
	private StatisticsSummaryRepository summaries;

	@Autowired
	private StatisticsProcessedEventRepository receipts;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private EntityManager entityManager;

	private final List<UUID> owners = new ArrayList<>();
	private final List<UUID> events = new ArrayList<>();
	private UUID ownerId;

	@BeforeEach
	void createOwner()
	{
		this.ownerId = owner();
	}

	@AfterEach
	void removeTestRecords()
	{
		transaction().executeWithoutResult(status ->
		{
			this.entityManager.createQuery("delete from StatisticsProcessedEvent e where e.eventId in :ids")
					.setParameter("ids", this.events).executeUpdate();
			this.entityManager.createQuery("delete from AppUser u where u.id in :ids")
					.setParameter("ids", this.owners).executeUpdate();
		});
	}

	@Test
	void emptyCollectionHasZeroCountsAndNoWinningGames()
	{
		assertThat(this.service.refresh(event(), this.ownerId)).isTrue();

		StatisticsSummary summary = summary();
		assertThat(summary.getCollectionSize()).isZero();
		assertThat(summary.getMostPlayed()).isNull();
		assertThat(summary.getMostRecentlyPlayed()).isNull();
		assertThat(summary.getPlaysForMonth(YearMonth.of(2026, 10))).isZero();
		assertThat(summary.getRefreshedAt()).isNotNull();
	}

	@Test
	void calculatesTheFourCardsWithoutIncludingAnotherOwnersData()
	{
		UUID azul = game(this.ownerId, "Azul", "");
		UUID wingspan = game(this.ownerId, "Wingspan", "Asia");
		game(this.ownerId, "Unplayed", "");
		play(azul, "2026-09-30");
		play(azul, "2026-10-01");
		play(azul, "2026-10-20");
		play(wingspan, "2026-10-21");
		play(game(owner(), "Other owner's game", ""), "2026-10-22");

		this.service.refresh(event(), this.ownerId);

		StatisticsSummary summary = summary();
		assertThat(summary.getCollectionSize()).isEqualTo(3);
		assertThat(summary.getMostPlayed().gameId()).isEqualTo(azul);
		assertThat(summary.getMostPlayed().playCount()).isEqualTo(3);
		assertThat(summary.getMostRecentlyPlayed().gameId()).isEqualTo(wingspan);
		assertThat(summary.getMostRecentlyPlayed().edition()).isEqualTo("Asia");
		assertThat(summary.getMostRecentlyPlayed().lastPlayedOn()).isEqualTo(LocalDate.of(2026, 10, 21));
		assertThat(summary.getPlaysForMonth(YearMonth.of(2026, 10))).isEqualTo(3);
		assertThat(summary.getPlaysForMonth(YearMonth.of(2026, 9))).isEqualTo(1);
	}

	@Test
	void monthLookupHandlesMonthAndYearRolloverWithoutAnotherEvent()
	{
		UUID gameId = game(this.ownerId, "Azul", "");
		play(gameId, "2025-12-31");
		play(gameId, "2026-01-01");
		play(gameId, "2026-01-31");
		play(gameId, "2026-02-01");

		this.service.refresh(event(), this.ownerId);

		StatisticsSummary summary = summary();
		assertThat(summary.getPlaysForMonth(YearMonth.of(2025, 12))).isEqualTo(1);
		assertThat(summary.getPlaysForMonth(YearMonth.of(2026, 1))).isEqualTo(2);
		assertThat(summary.getPlaysForMonth(YearMonth.of(2026, 2))).isEqualTo(1);
		assertThat(summary.getPlaysForMonth(YearMonth.of(2027, 1))).isZero();
	}

	@Test
	void tiesUseGameNameThenEditionAndUnplayedGamesAreNotWinners()
	{
		game(this.ownerId, "A never-played game", "");
		UUID wingspan = game(this.ownerId, "Wingspan", "");
		UUID azulZ = game(this.ownerId, "Azul", "Z");
		UUID azulA = game(this.ownerId, "Azul", "A");
		play(wingspan, "2026-10-21");
		play(azulZ, "2026-10-21");
		play(azulA, "2026-10-21");

		this.service.refresh(event(), this.ownerId);

		assertThat(summary().getMostPlayed().gameId()).isEqualTo(azulA);
		assertThat(summary().getMostRecentlyPlayed().gameId()).isEqualTo(azulA);
	}

	@Test
	void duplicateIsSkippedButNewEventRefreshesRenamesAndEditedPlayDates()
	{
		UUID gameId = game(this.ownerId, "Azul", "");
		UUID playId = play(gameId, "2026-10-21");
		UUID firstEvent = event();
		this.service.refresh(firstEvent, this.ownerId);

		transaction().executeWithoutResult(status ->
		{
			this.entityManager.find(BoardGame.class, gameId)
					.updateDetails("Renamed Azul", "New edition", null, null, null, null, null);
			this.entityManager.find(GamePlay.class, playId)
					.updateDetails(LocalDate.of(2026, 9, 15), null, null, null);
		});

		assertThat(this.service.refresh(firstEvent, this.ownerId)).isFalse();
		assertThat(summary().getMostPlayed().name()).isEqualTo("Azul");
		assertThat(this.service.refresh(event(), this.ownerId)).isTrue();
		assertThat(summary().getMostPlayed().name()).isEqualTo("Renamed Azul");
		assertThat(summary().getMostRecentlyPlayed().edition()).isEqualTo("New edition");
		assertThat(summary().getPlaysForMonth(YearMonth.of(2026, 10))).isZero();
		assertThat(summary().getPlaysForMonth(YearMonth.of(2026, 9))).isEqualTo(1);
	}

	@Test
	void deletedPlayAndGameDisappearEvenWhenAnOlderEventArrivesLater()
	{
		UUID gameId = game(this.ownerId, "Azul", "");
		UUID playId = play(gameId, "2026-10-21");
		UUID delayedEvent = event();
		this.service.refresh(event(), this.ownerId);

		transaction().executeWithoutResult(status ->
				this.entityManager.remove(this.entityManager.find(GamePlay.class, playId)));
		this.service.refresh(event(), this.ownerId);
		assertThat(summary().getCollectionSize()).isEqualTo(1);
		assertThat(summary().getMostPlayed()).isNull();
		assertThat(summary().getMostRecentlyPlayed()).isNull();
		assertThat(summary().getPlaysForMonth(YearMonth.of(2026, 10))).isZero();

		transaction().executeWithoutResult(status ->
				this.entityManager.remove(this.entityManager.find(BoardGame.class, gameId)));
		this.service.refresh(delayedEvent, this.ownerId);
		assertThat(summary().getCollectionSize()).isZero();
		assertThat(summary().getMostPlayed()).isNull();
	}

	@Test
	void failureAfterSavingRollsBackBothSummaryAndReceipt()
	{
		UUID eventId = event();
		game(this.ownerId, "Azul", "");
		doAnswer(invocation ->
		{
			StatisticsSummary summary = invocation.getArgument(0);
			this.entityManager.merge(summary);
			this.entityManager.flush();
			throw new IllegalStateException("Simulated failure after saving statistics");
		}).when(this.summaries).saveAndFlush(any(StatisticsSummary.class));

		assertThatThrownBy(() -> this.service.refresh(eventId, this.ownerId))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Simulated failure after saving statistics");
		assertThat(this.summaries.findById(this.ownerId)).isEmpty();
		assertThat(this.receipts.findById(eventId)).isEmpty();
	}

	@Test
	void missingOwnerFailsWithoutRecordingSuccess()
	{
		UUID eventId = event();
		assertThatThrownBy(() -> this.service.refresh(eventId, UUID.randomUUID()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Statistics event owner does not exist.");
		assertThat(this.receipts.findById(eventId)).isEmpty();
	}

	private UUID owner()
	{
		UUID id = transaction().execute(status ->
		{
			AppUser owner = new AppUser("https://example.test/", "statistics-" + UUID.randomUUID());
			this.entityManager.persist(owner);
			return owner.getId();
		});
		this.owners.add(id);
		return id;
	}

	private UUID game(UUID owner, String name, String edition)
	{
		return transaction().execute(status ->
		{
			BoardGame game = new BoardGame(this.entityManager.find(AppUser.class, owner),
					name, edition, null, null, null, null, null);
			this.entityManager.persist(game);
			return game.getId();
		});
	}

	private UUID play(UUID gameId, String playedOn)
	{
		return transaction().execute(status ->
		{
			GamePlay play = new GamePlay(this.entityManager.find(BoardGame.class, gameId),
					LocalDate.parse(playedOn), null, null, null);
			this.entityManager.persist(play);
			return play.getId();
		});
	}

	private UUID event()
	{
		UUID id = UUID.randomUUID();
		this.events.add(id);
		return id;
	}

	private StatisticsSummary summary()
	{
		return this.summaries.findById(this.ownerId).orElseThrow();
	}

	private TransactionTemplate transaction()
	{
		return new TransactionTemplate(this.transactionManager);
	}
}

package com.SwampDragonInn.trackmyboardgames.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.domain.StatisticsSummary;
import com.SwampDragonInn.trackmyboardgames.domain.StatisticsSummary.GameHighlight;
import com.SwampDragonInn.trackmyboardgames.repository.AppUserRepository;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsProcessedEventRepository;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsSourceRepository;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsSourceRepository.GameTotals;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsSourceRepository.MonthTotal;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsSummaryRepository;

// Rebuilds the four statistics cards when the receiver delivers a game or play event.
// The event is a prompt to check current records, not an instruction to add or subtract one:
// that makes late-arriving events safe, including edits and deletions. The saved cards and the
// event's receipt commit together; a failed refresh rolls both back so RabbitMQ can retry.
@Service
public class StatisticsRefreshService
{

	private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsRefreshService.class);
	private final AppUserRepository users;
	private final StatisticsSourceRepository source;
	private final StatisticsSummaryRepository summaries;
	private final StatisticsProcessedEventRepository processedEvents;

	// Keeps source reads, saved statistics, and duplicate receipts in their existing repository layers.
	public StatisticsRefreshService(AppUserRepository users, StatisticsSourceRepository source,
			StatisticsSummaryRepository summaries, StatisticsProcessedEventRepository processedEvents)
	{
		this.users = users;
		this.source = source;
		this.summaries = summaries;
		this.processedEvents = processedEvents;
	}

	// Refreshes one owner's cards for a new event. Returns false when the same event was already handled.
	// Only the validated receiver should call this; it is not a public API.
	@Transactional
	public boolean refresh(UUID eventId, UUID ownerId)
	{
		Objects.requireNonNull(eventId, "Event ID is required.");
		Objects.requireNonNull(ownerId, "Owner ID is required.");

		// Wait our turn before recalculating this owner's statistics. The database lock stays
		// in place until this whole transaction succeeds or rolls back.
		this.users
			.findForStatisticsRefresh(ownerId)
			.orElseThrow(() -> new IllegalStateException("Statistics event owner does not exist."));

		// Skip a repeated event. If a later step fails, this new receipt rolls back as well.
		Instant now = Instant.now();
		if (this.processedEvents.recordIfNew(eventId, now) == 0)
		{
			return false;
		}

		rebuildSummary(ownerId, now);
		return true;
	}

	// Calculates starting statistics without inventing a RabbitMQ event or processed-event receipt.
	// Uses the same owner lock as live events so a backfill and a receiver cannot save over one another.
	// The caller processes users separately, so this transaction only covers one user's calculations.
	@Transactional
	public boolean backfill(UUID ownerId)
	{
		Objects.requireNonNull(ownerId, "Owner ID is required.");
		if (this.users.findForStatisticsRefresh(ownerId).isEmpty())
		{
			// An account may be deleted after the backfill reads its ID but before it reaches this step.
			LOGGER.warn("Skipping statistics backfill for owner {} because the account no longer exists.", ownerId);
			return false;
		}
		rebuildSummary(ownerId, Instant.now());
		return true;
	}

	// Both event processing and backfill use this calculation, keeping the four cards consistent.
	private void rebuildSummary(UUID ownerId, Instant now)
	{
		// Load one set of totals for each game, including games that have never been played.
		List<GameTotals> games = this.source.findGameTotals(ownerId);

		// Give tied games a consistent order: name first, then edition, then their unique ID.
		Comparator<GameTotals> alphabetical = Comparator
				.comparing((GameTotals game) -> game.getName(), String.CASE_INSENSITIVE_ORDER)
				.thenComparing(game -> game.getEdition(), String.CASE_INSENSITIVE_ORDER)
				.thenComparing(game -> game.getGameId());

		GameHighlight mostPlayed = findMostPlayed(games, alphabetical);
		GameHighlight mostRecent = findMostRecentlyPlayed(games, alphabetical);
		Map<String, Long> months = countPlaysByMonth(ownerId);

		// Save all four cards together. The number of game totals is the collection size.
		StatisticsSummary summary = new StatisticsSummary(
				ownerId, games.size(), mostPlayed, mostRecent, months, now);
		this.summaries.saveAndFlush(summary);
	}

	// Chooses the game with the highest play count; games with no plays cannot win this card.
	private GameHighlight findMostPlayed(List<GameTotals> games, Comparator<GameTotals> alphabetical)
	{
		Comparator<GameTotals> mostPlaysFirst = Comparator
				.comparingLong((GameTotals game) -> game.getPlayCount())
				.reversed()
				.thenComparing(alphabetical);

		return games.stream()
				.filter(game -> game.getPlayCount() > 0)
				.sorted(mostPlaysFirst)
				.findFirst()
				.map(game -> highlight(game))
				.orElse(null);
	}

	// Chooses the game with the newest played-on date, ignoring games that have never been played.
	private GameHighlight findMostRecentlyPlayed(List<GameTotals> games, Comparator<GameTotals> alphabetical)
	{
		Comparator<GameTotals> newestDateFirst = Comparator
				.comparing((GameTotals game) -> game.getLastPlayedOn())
				.reversed()
				.thenComparing(alphabetical);

		return games.stream()
				.filter(game -> game.getLastPlayedOn() != null)
				.sorted(newestDateFirst)
				.findFirst()
				.map(game -> highlight(game))
				.orElse(null);
	}

	// Stores each month's count under a year-month key, such as "2026-10".
	// The future read endpoint can look up the current month without recalculating the history.
	private Map<String, Long> countPlaysByMonth(UUID ownerId)
	{
		Map<String, Long> months = new HashMap<>();
		for (MonthTotal total : this.source.findMonthlyTotals(ownerId))
		{
			months.put(total.getMonth(), total.getPlayCount());
		}
		return months;
	}

	// Copies only the fields needed to display a winning game on a card.
	private GameHighlight highlight(GameTotals game)
	{
		return new GameHighlight(
            game.getGameId(), 
            game.getName(), 
            game.getEdition(),
			game.getPlayCount(), 
            game.getLastPlayedOn());
	}
}

package com.SwampDragonInn.trackmyboardgames.service;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.SwampDragonInn.trackmyboardgames.api.CollectionStatisticsResponse;
import com.SwampDragonInn.trackmyboardgames.api.MonthlyHeatmapResponse;
import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.CollectionStatisticsSummary;
import com.SwampDragonInn.trackmyboardgames.repository.AppUserRepository;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsSummaryRepository;
import com.SwampDragonInn.trackmyboardgames.repository.CollectionStatisticsRepository;
import com.SwampDragonInn.trackmyboardgames.repository.GameCollectionRepository;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsHeatmapRepository;

// Reads already-calculated statistics for the signed-in user.
// Opening or refreshing the page fetches the saved answers instead of calculating them again.
@Service
public class StatisticsReadService
{

	private final AppUserRepository users;
	private final StatisticsSummaryRepository summaryRepository;
	private final CollectionStatisticsRepository collectionSummaries;
	private final GameCollectionRepository collections;
	private final StatisticsHeatmapRepository heatmapRepository;

	public StatisticsReadService(AppUserRepository users, StatisticsSummaryRepository summaryRepository,
			CollectionStatisticsRepository collectionSummaries, GameCollectionRepository collections,
			StatisticsHeatmapRepository heatmapRepository)
	{
		this.users = users;
		this.summaryRepository = summaryRepository;
		this.collectionSummaries = collectionSummaries;
		this.collections = collections;
		this.heatmapRepository = heatmapRepository;
	}

	/**
	 * Reads saved account-wide statistics for callers that do not choose a collection.
	 */
	@Transactional(readOnly = true)
	public CollectionStatisticsResponse readCollectionStats(String issuer, String subject)
	{
		return readCollectionStats(issuer, subject, null);
	}

	/**
	 * Reads saved answers for the account or one owned collection without recounting games and plays.
	 */
	@Transactional(readOnly = true)
	public CollectionStatisticsResponse readCollectionStats(String issuer, String subject, UUID collectionId)
	{
		// Decide which month "plays this month" means using UTC time.
		// When a new month starts, show that month's count even if no games or plays have changed.
		YearMonth month = YearMonth.now(ZoneOffset.UTC);
		// Find the local account that matches this login, then look up its saved statistics.
		AppUser owner = findOwnerForScope(issuer, subject, collectionId);

		// Check an explicitly chosen collection before reading its answers. Never fall back to account totals.
		CollectionStatisticsSummary summary;
		if (collectionId != null)
		{
			summary = this.collectionSummaries.findByCollectionAndOwner(collectionId, owner.getId()).orElse(null);
		}
		else
		{
			summary = owner == null ? null : this.summaryRepository.findById(owner.getId()).orElse(null);
		}

		if (summary == null)
		{
			// Leave missing results empty; do not pretend the user has zero games.
			return new CollectionStatisticsResponse(null, null, null, null, month.toString(), "UTC", null);
		}

		// Send back the four cards and when they were last calculated.
		return new CollectionStatisticsResponse(summary.getCollectionSize(), summary.getMostPlayed(),
				summary.getPlaysForMonth(month), summary.getMostRecentlyPlayed(),
				month.toString(), "UTC", summary.getRefreshedAt());
	}

	/**
	 * Builds the current month's heatmap from saved statistics, using UTC to choose the month.
	 * Each date shows its saved play count, or zero if no plays were recorded that day.
	 * If statistics have not been calculated yet, leaves the daily results empty rather than showing zeros.
	 */
	@Transactional(readOnly = true)
	public MonthlyHeatmapResponse readMonthlyHeatmap(String issuer, String subject, UUID collectionId)
	{
		// Check the selected scope before loading any saved daily counts.
		YearMonth month = YearMonth.now(ZoneOffset.UTC);
		AppUser owner = findOwnerForScope(issuer, subject, collectionId);
		var saved = owner == null ? null : this.heatmapRepository.findSaved(owner.getId(), collectionId).orElse(null);

		if (saved == null)
		{
			return new MonthlyHeatmapResponse(month.toString(), "UTC", null, null);
		}

		// Include all calendar days, even days with no plays, without recounting history.
		List<MonthlyHeatmapResponse.Day> days = IntStream.rangeClosed(1, month.lengthOfMonth())
				.mapToObj(day -> month.atDay(day))
				.map(date -> new MonthlyHeatmapResponse.Day(date, saved.dailyPlays().getOrDefault(date.toString(), 0L)))
				.toList();
		return new MonthlyHeatmapResponse(month.toString(), "UTC", days, saved.refreshedAt());
	}

	/**
	 * Finds the local account for the signed-in user.
	 * If a collection was requested, checks that it exists and belongs to that account;
	 * otherwise, stops the request with a "Collection not found" error.
	 * Returns null if the user has no local account yet and no collection was requested.
	 */
	private AppUser findOwnerForScope(String issuer, String subject, UUID collectionId)
	{
		AppUser owner = this.users.findByAuth0IssuerAndAuth0Subject(issuer, subject).orElse(null);
		if (collectionId != null
				&& (owner == null || this.collections.findByIdAndOwner_Id(collectionId, owner.getId()).isEmpty()))
		{
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection not found.");
		}
		return owner;
	}
}

package com.SwampDragonInn.trackmyboardgames.service;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.SwampDragonInn.trackmyboardgames.api.CollectionStatisticsResponse;
import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.CollectionStatisticsSummary;
import com.SwampDragonInn.trackmyboardgames.repository.AppUserRepository;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsSummaryRepository;
import com.SwampDragonInn.trackmyboardgames.repository.CollectionStatisticsRepository;
import com.SwampDragonInn.trackmyboardgames.repository.GameCollectionRepository;

// Reads already-calculated statistics for the signed-in user.
// Opening or refreshing the page fetches the saved answers instead of calculating them again.
@Service
public class StatisticsReadService
{

	private final AppUserRepository users;
	private final StatisticsSummaryRepository summaryRepository;
	private final CollectionStatisticsRepository collectionSummaries;
	private final GameCollectionRepository collections;

	public StatisticsReadService(AppUserRepository users, StatisticsSummaryRepository summaryRepository,
			CollectionStatisticsRepository collectionSummaries, GameCollectionRepository collections)
	{
		this.users = users;
		this.summaryRepository = summaryRepository;
		this.collectionSummaries = collectionSummaries;
		this.collections = collections;
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
		AppUser owner = this.users.findByAuth0IssuerAndAuth0Subject(issuer, subject).orElse(null);

		// Check an explicitly chosen collection before reading its answers. Never fall back to account totals.
		CollectionStatisticsSummary summary;
		if (collectionId != null)
		{
			if (owner == null || this.collections.findByIdAndOwner_Id(collectionId, owner.getId()).isEmpty())
			{
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection not found.");
			}
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
}

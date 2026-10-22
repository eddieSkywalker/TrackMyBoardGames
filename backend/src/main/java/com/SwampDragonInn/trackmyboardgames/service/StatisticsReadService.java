package com.SwampDragonInn.trackmyboardgames.service;

import java.time.YearMonth;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.api.StatisticsResponse;
import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.StatisticsSummary;
import com.SwampDragonInn.trackmyboardgames.repository.AppUserRepository;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsSummaryRepository;

// Reads already-calculated statistics for the signed-in user.
// Opening or refreshing the page fetches the saved answers instead of calculating them again.
@Service
public class StatisticsReadService
{

	private final AppUserRepository users;
	private final StatisticsSummaryRepository summaries;

	public StatisticsReadService(AppUserRepository users, StatisticsSummaryRepository summaries)
	{
		this.users = users;
		this.summaries = summaries;
	}

	// Uses the user's login details to find their saved statistics, so they only see their own results.
	@Transactional(readOnly = true)
	public StatisticsResponse read(String issuer, String subject)
	{
		// Decide which month "plays this month" means using UTC time.
		// When a new month starts, show that month's count even if no games or plays have changed.
		YearMonth month = YearMonth.now(ZoneOffset.UTC);
		// Find the local account that matches this login, then look up its saved statistics.
		AppUser owner = this.users.findByAuth0IssuerAndAuth0Subject(issuer, subject).orElse(null);
		StatisticsSummary summary = owner == null
				? null
				: this.summaries.findById(owner.getId()).orElse(null);

		if (summary == null)
		{
			// Leave missing results empty; do not pretend the user has zero games.
			return new StatisticsResponse(null, null, null, null, month.toString(), "UTC", null);
		}

		// Send back the four cards and when they were last calculated.
		return new StatisticsResponse(summary.getCollectionSize(), summary.getMostPlayed(),
				summary.getPlaysForMonth(month), summary.getMostRecentlyPlayed(),
				month.toString(), "UTC", summary.getRefreshedAt());
	}
}

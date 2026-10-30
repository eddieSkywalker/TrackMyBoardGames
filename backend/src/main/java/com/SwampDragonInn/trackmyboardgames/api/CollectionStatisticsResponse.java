package com.SwampDragonInn.trackmyboardgames.api;

import java.time.Instant;

import com.SwampDragonInn.trackmyboardgames.domain.CollectionStatisticsSummary.GameSummary;

// The information sent to the Statistics component on the collection page: collection size,
// most-played game, plays this month, and most recently played game.
// If statistics have not been calculated yet, the counts and game details are null (empty),
// rather than zero; this does not mean the collection is empty.
// month says which month was counted, such as "2026-10", and timezone says which timezone
// was used to decide the current month. refreshedAt says when the statistics were last calculated.
public record CollectionStatisticsResponse(
		Long collectionSize,
		GameSummary mostPlayed,
		Long playsThisMonth,
		GameSummary mostRecentlyPlayed,
		String month,
		String timezone,
		Instant refreshedAt)
{
}

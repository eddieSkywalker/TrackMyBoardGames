package com.SwampDragonInn.trackmyboardgames.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Reads daily play totals previously calculated by the background statistics update.
 * Can read totals across all of a user's collections or for one collection they own.
 */
@Repository
public class StatisticsHeatmapRepository
{

	private final JdbcTemplate jdbc;
	private final JsonMapper jsonMapper;

	public StatisticsHeatmapRepository(JdbcTemplate jdbc, JsonMapper jsonMapper)
	{
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
	}

	public record SavedDailyCounts(Map<String, Long> dailyPlays, Instant refreshedAt)
	{
	}

	/**
	 * Loads saved daily play totals from the statistics tables instead of counting play history again.
	 */
	public Optional<SavedDailyCounts> findSaved(UUID ownerId, UUID collectionId)
	{
		// Choose all collections or one collection, and supply the IDs for the query's question marks.
		String query;
		Object[] ownerAndCollectionIds;
		if (collectionId == null)
		{
			query = "select daily_plays, refreshed_at from statistics_summaries where owner_id = ?";
			ownerAndCollectionIds = new Object[] { ownerId };
		}
		else
		{
			query = "select daily_plays, refreshed_at from collection_statistics_summaries where owner_id = ? and collection_id = ?";
			ownerAndCollectionIds = new Object[] { ownerId, collectionId };
		}

		// Convert the saved JSON and refresh time into the values the service uses.
		List<SavedDailyCounts> results = this.jdbc.query(query, (row, rowNumber) ->
		{
			String dailyPlaysJson = row.getString("daily_plays");
			var dailyPlaysType = this.jsonMapper.getTypeFactory().constructMapType(Map.class, String.class, Long.class);
			Map<String, Long> dailyPlays = this.jsonMapper.readValue(dailyPlaysJson, dailyPlaysType);
			Instant refreshedAt = row.getTimestamp("refreshed_at").toInstant();

			return new SavedDailyCounts(dailyPlays, refreshedAt);
		}, ownerAndCollectionIds);

		if (results.isEmpty())
		{
			return Optional.empty();
		}

		return Optional.of(results.getFirst());
	}
}

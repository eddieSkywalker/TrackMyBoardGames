package com.SwampDragonInn.trackmyboardgames.repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.SwampDragonInn.trackmyboardgames.domain.CollectionStatisticsSummary;
import com.SwampDragonInn.trackmyboardgames.domain.CollectionStatisticsSummary.GameSummary;

import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the saved statistics for one collection, checking its owner as well as its ID.
 */
@Repository
public class CollectionStatisticsRepository
{

	private final JdbcTemplate jdbc;
	private final JsonMapper jsonMapper;

	public CollectionStatisticsRepository(JdbcTemplate jdbc, JsonMapper jsonMapper)
	{
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * Loads the saved answers only; the background refresh handles counting games and plays.
	 */
	public Optional<CollectionStatisticsSummary> findByCollectionAndOwner(UUID collectionId, UUID ownerId)
	{
		return this.jdbc.query("""
				select collection_size, most_played, most_recently_played, monthly_plays, refreshed_at
				from collection_statistics_summaries where collection_id = ? and owner_id = ?
				""", (row, rowNumber) -> new CollectionStatisticsSummary(ownerId, row.getLong("collection_size"),
						readGame(row.getString("most_played")), readGame(row.getString("most_recently_played")),
						this.jsonMapper.readValue(row.getString("monthly_plays"),
								this.jsonMapper.getTypeFactory().constructMapType(Map.class, String.class, Long.class)),
						row.getTimestamp("refreshed_at").toInstant()), collectionId, ownerId)
				.stream().findFirst();
	}

	private GameSummary readGame(String savedGame)
	{
		return savedGame == null ? null : this.jsonMapper.readValue(savedGame, GameSummary.class);
	}
}

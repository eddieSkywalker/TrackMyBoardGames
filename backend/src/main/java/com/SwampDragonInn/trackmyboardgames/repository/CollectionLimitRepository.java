package com.SwampDragonInn.trackmyboardgames.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.SwampDragonInn.trackmyboardgames.service.CollectionTierLimits;

/**
 * Reads the current game limits for all collection tiers from the database.
 */
@Repository
public class CollectionLimitRepository
{

	private final JdbcTemplate jdbc;

	public CollectionLimitRepository(JdbcTemplate jdbc)
	{
		this.jdbc = jdbc;
	}

	/**
	 * Loads the latest saved limits whose start date has arrived.
	 * Missing limits stop the operation rather than using guessed allowances.
	 */
	public CollectionTierLimits findCurrent()
	{
		// The database clock keeps scheduled changes consistent across app servers.
		return this.jdbc.query("""
				select free_limit, home_limit, pro_limit, business_limit
				from collection_limit_versions
				where effective_from <= current_timestamp
				order by effective_from desc
				limit 1
				""", (row, rowNumber) -> new CollectionTierLimits(row.getInt("free_limit"), row.getInt("home_limit"),
						row.getInt("pro_limit"), row.getInt("business_limit")))
				.stream().findFirst()
				.orElseThrow(() -> new IllegalStateException("No collection game limits are available for the current date."));
	}
}

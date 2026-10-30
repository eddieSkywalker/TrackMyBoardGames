package com.SwampDragonInn.trackmyboardgames.repository;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.SwampDragonInn.trackmyboardgames.service.CollectionPrices;

/**
 * Reads the current collection prices and their saved version ID from the database.
 */
@Repository
public class CollectionPriceRepository
{

	private final JdbcTemplate jdbc;

	public CollectionPriceRepository(JdbcTemplate jdbc)
	{
		this.jdbc = jdbc;
	}

	/**
	 * Holds one saved price list and the ID that payments use to identify it.
	 */
	public record PriceVersion(UUID id, CollectionPrices prices)
	{
	}

	/**
	 * Loads the latest price list whose start date has arrived.
	 * Missing prices stop the operation so payments cannot use guessed amounts.
	 */
	public PriceVersion findCurrent()
	{
		// Use the database clock so all app servers agree about when scheduled prices take effect.
		return this.jdbc.query("""
				select id, home_cents, pro_cents, business_monthly_cents
				from collection_price_versions
				where effective_from <= current_timestamp
				order by effective_from desc
				limit 1
				""", (row, rowNumber) -> new PriceVersion(row.getObject("id", UUID.class),
						new CollectionPrices(row.getLong("home_cents"), row.getLong("pro_cents"),
								row.getLong("business_monthly_cents"))))
				.stream().findFirst()
				.orElseThrow(() -> new IllegalStateException("No collection prices are available for the current date."));
	}
}

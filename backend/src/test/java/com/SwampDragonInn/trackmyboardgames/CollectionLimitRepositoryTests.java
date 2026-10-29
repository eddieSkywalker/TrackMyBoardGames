package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.CollectionSubscriptionStatus;
import com.SwampDragonInn.trackmyboardgames.domain.CollectionTier;
import com.SwampDragonInn.trackmyboardgames.domain.GameCollection;
import com.SwampDragonInn.trackmyboardgames.repository.CollectionLimitRepository;
import com.SwampDragonInn.trackmyboardgames.service.CollectionAccessPolicy;
import com.SwampDragonInn.trackmyboardgames.service.CollectionTierLimits;

/**
 * Checks saved game limits, scheduled changes, and protection against accidental rewrites.
 */
@SpringBootTest
class CollectionLimitRepositoryTests
{

	@Autowired
	private CollectionLimitRepository limits;

	@Autowired
	private CollectionAccessPolicy policy;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource dataSource;

	@Test
	@Transactional
	void latestStartedLimitsApplyToEveryTierWhileFutureLimitsWait()
	{
		insertLimits("current_timestamp", 3, 5, 7, 9);
		insertLimits("current_timestamp + interval '1 day'", 30, 50, 70, 90);

		assertThat(this.limits.findCurrent()).isEqualTo(new CollectionTierLimits(3, 5, 7, 9));

		GameCollection collection = new GameCollection(new AppUser("issuer", "limit-test"), "Limits");
		Instant now = Instant.now();
		for (CollectionTier tier : new CollectionTier[] { CollectionTier.FREE, CollectionTier.HOME, CollectionTier.PRO })
		{
			collection.setPermanentTier(tier);
			assertThat(this.policy.gameLimit(collection, now))
					.isEqualTo(this.limits.findCurrent().forTier(tier));
		}

		collection.setPlanTier(CollectionTier.BUSINESS);
		collection.setSubscriptionStatus(CollectionSubscriptionStatus.ACTIVE);
		collection.setSubscriptionEndsAt(now.plusSeconds(60));
		assertThat(this.policy.gameLimit(collection, now)).isEqualTo(9);
	}

	@ParameterizedTest
	@Transactional
	@ValueSource(strings = {
			"update collection_limit_versions set free_limit = 1",
			"delete from collection_limit_versions",
			"truncate collection_limit_versions",
			"insert into collection_limit_versions (effective_from, free_limit, home_limit, pro_limit, business_limit, change_reason) "
					+ "values (current_timestamp, 0, 100, 200, 1000, 'Invalid limit')",
			"insert into collection_limit_versions (effective_from, free_limit, home_limit, pro_limit, business_limit, change_reason) "
					+ "values (current_timestamp, 20, -1, 200, 1000, 'Invalid limit')",
			"insert into collection_limit_versions (effective_from, free_limit, home_limit, pro_limit, business_limit, change_reason) "
					+ "values (current_timestamp, 20, 100, 0, 1000, 'Invalid limit')",
			"insert into collection_limit_versions (effective_from, free_limit, home_limit, pro_limit, business_limit, change_reason) "
					+ "values (current_timestamp, 20, 100, 200, -1, 'Invalid limit')",
			"insert into collection_limit_versions (effective_from, free_limit, home_limit, pro_limit, business_limit, change_reason) "
					+ "values (current_timestamp, 20, 100, 200, 1000, ' ')"
	})
	void databaseRejectsRewritesAndInvalidNewLimits(String sql)
	{
		assertThatThrownBy(() -> this.jdbc.execute(sql)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void migrationSeedsLimitsOnceAndLaterDeploymentsLeaveNewLimitsAlone()
	{
		String schema = "limit_migration_" + UUID.randomUUID().toString().replace("-", "");

		try
		{
			Flyway.configure().dataSource(this.dataSource).defaultSchema(schema).schemas(schema)
					.target("16").load().migrate();
			Flyway flyway = Flyway.configure().dataSource(this.dataSource).defaultSchema(schema).schemas(schema).load();
			flyway.migrate();

			assertThat(this.jdbc.queryForMap("select free_limit, home_limit, pro_limit, business_limit from "
					+ schema + ".collection_limit_versions"))
					.containsEntry("free_limit", 20).containsEntry("home_limit", 100)
					.containsEntry("pro_limit", 200).containsEntry("business_limit", 1000);

			UUID nextVersion = UUID.randomUUID();
			this.jdbc.update("insert into " + schema + """
					.collection_limit_versions
					(id, effective_from, free_limit, home_limit, pro_limit, business_limit, change_reason)
					values (?, current_timestamp, 30, 150, 300, 1500, 'Approved limit change')
					""", nextVersion);

			// A later deployment checks completed migrations without resetting the saved limits.
			flyway.migrate();

			assertThat(this.jdbc.queryForObject("select id from " + schema
					+ ".collection_limit_versions order by effective_from desc limit 1", UUID.class)).isEqualTo(nextVersion);
			assertThat(this.jdbc.queryForObject("select count(*) from " + schema
					+ ".collection_limit_versions", Long.class)).isEqualTo(2);
		}
		finally
		{
			this.jdbc.execute("drop schema if exists " + schema + " cascade");
		}
	}

	private void insertLimits(String startDateSql, int free, int home, int pro, int business)
	{
		this.jdbc.update("""
				insert into collection_limit_versions
				(effective_from, free_limit, home_limit, pro_limit, business_limit, change_reason)
				values (%s, ?, ?, ?, ?, 'Limit history test')
				""".formatted(startDateSql), free, home, pro, business);
	}
}

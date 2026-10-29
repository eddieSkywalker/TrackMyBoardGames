package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Preserves V14 access rights without making up payment amounts or historical paid months.
 */
@SpringBootTest
class CollectionPaymentMigrationTests
{

	@Autowired
	private DataSource dataSource;

	@Test
	void priceMigrationKeepsOldPaymentsAndLaterDeploymentsLeaveNewPricesAlone()
	{
		String schema = "price_migration_" + UUID.randomUUID().toString().replace("-", "");
		JdbcTemplate jdbc = new JdbcTemplate(this.dataSource);

		try
		{
			Flyway.configure().dataSource(this.dataSource).defaultSchema(schema).schemas(schema)
					.target("15").load().migrate();
			UUID owner = UUID.randomUUID();
			UUID collection = UUID.randomUUID();
			UUID payment = UUID.randomUUID();
			jdbc.update("insert into " + schema + """
					.app_users (id, auth0_issuer, auth0_subject, created_at)
					values (?, 'issuer', 'existing-payment', current_timestamp)
					""", owner);
			jdbc.update("insert into " + schema + """
					.game_collections (id, owner_id, name, created_at)
					values (?, ?, 'Existing shelf', current_timestamp)
					""", collection, owner);
			jdbc.update("insert into " + schema + """
					.collection_payments (id, owner_id, collection_id, payment_reference, amount_cents, currency, paid_at)
					values (?, ?, ?, 'existing-payment', 3000, 'USD', current_timestamp)
					""", payment, owner, collection);

			Flyway flyway = Flyway.configure().dataSource(this.dataSource).defaultSchema(schema).schemas(schema).load();
			flyway.migrate();

			assertThat(jdbc.queryForObject("select price_version_id from " + schema + ".collection_payments where id = ?",
					UUID.class, payment)).isNull();
			assertThat(jdbc.queryForObject("select amount_cents from " + schema + ".collection_payments where id = ?",
					Long.class, payment)).isEqualTo(3000);
			assertThat(jdbc.queryForMap("select home_cents, pro_cents, business_monthly_cents from "
					+ schema + ".collection_price_versions"))
					.containsEntry("home_cents", 3000L).containsEntry("pro_cents", 5000L)
					.containsEntry("business_monthly_cents", 3000L);

			UUID newVersion = UUID.randomUUID();
			jdbc.update("insert into " + schema + """
					.collection_price_versions
					(id, effective_from, home_cents, pro_cents, business_monthly_cents, change_reason)
					values (?, current_timestamp, 4000, 2000, 3500, 'Deliberate price change')
					""", newVersion);

			// A later deployment reruns migration checks, not the initial price insert.
			flyway.migrate();

			assertThat(jdbc.queryForObject("select id from " + schema
					+ ".collection_price_versions order by effective_from desc limit 1", UUID.class)).isEqualTo(newVersion);
			assertThat(jdbc.queryForObject("select count(*) from " + schema
					+ ".collection_price_versions", Long.class)).isEqualTo(2);
		}
		finally
		{
			jdbc.execute("drop schema if exists " + schema + " cascade");
		}
	}

	@Test
	void migrationMovesAccessOntoCollectionsAndKeepsPreviouslyGrantedRights()
	{
		String schema = "payment_migration_" + UUID.randomUUID().toString().replace("-", "");
		JdbcTemplate jdbc = new JdbcTemplate(this.dataSource);

		try
		{
			Flyway.configure().dataSource(this.dataSource).defaultSchema(schema).schemas(schema)
					.target("14").load().migrate();
			UUID owner = UUID.randomUUID();
			UUID collection = UUID.randomUUID();
			jdbc.update("insert into " + schema + """
					.app_users (id, auth0_issuer, auth0_subject, created_at)
					values (?, 'issuer', 'existing', current_timestamp)
					""", owner);
			jdbc.update("insert into " + schema + """
					.game_collections (id, owner_id, name, created_at, background_image, plan_tier)
					values (?, ?, 'Preserved shelf', current_timestamp, decode('010203', 'hex'), 'BUSINESS')
					""", collection, owner);
			jdbc.update("insert into " + schema + """
					.collection_entitlements (collection_id, permanent_tier, subscription_status, subscription_ends_at)
					values (?, 'PRO', 'ACTIVE', '2027-01-01T00:00:00Z')
					""", collection);
			jdbc.update("update " + schema + ".app_users set default_collection_id = ? where id = ?", collection, owner);
			Flyway.configure().dataSource(this.dataSource).defaultSchema(schema).schemas(schema).load().migrate();

			assertThat(jdbc.queryForMap("select plan_tier, permanent_tier, subscription_status, total_payments, subscriber_months "
					+ "from " + schema + ".game_collections where id = ?", collection))
					.containsEntry("plan_tier", "BUSINESS").containsEntry("permanent_tier", "PRO")
					.containsEntry("subscription_status", "ACTIVE")
					.containsEntry("total_payments", 0L).containsEntry("subscriber_months", 0L);
			assertThat(jdbc.queryForObject("select background_image from " + schema + ".game_collections where id = ?",
					byte[].class, collection)).containsExactly((byte) 1, (byte) 2, (byte) 3);
			assertThat(jdbc.queryForObject("select default_collection_id from " + schema + ".app_users where id = ?",
					UUID.class, owner)).isEqualTo(collection);
			assertThat(jdbc.queryForObject("select count(*) from " + schema + ".collection_payments", Long.class)).isZero();
			assertThat(jdbc.queryForObject("""
					select count(*) from information_schema.tables where table_schema = ? and table_name = 'collection_entitlements'
					""", Long.class, schema)).isZero();
		}
		finally
		{
			jdbc.execute("drop schema if exists " + schema + " cascade");
		}
	}
}

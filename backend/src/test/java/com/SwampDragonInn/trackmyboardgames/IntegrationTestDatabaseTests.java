package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Checks that normal Spring tests use their own migrated tables and cannot start live background workers.
 */
@SpringBootTest
class IntegrationTestDatabaseTests
{

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Environment environment;

	@Test
	void usesGeneratedSchemaForBothDatabaseQueriesAndMigrations()
	{
		String schema = this.jdbc.queryForObject("select current_schema()", String.class);
		assertThat(schema).matches("tmbg_test_[a-f0-9]{32}");
		assertThat(this.environment.getProperty("spring.flyway.default-schema")).isEqualTo(schema);
		assertThat(this.environment.getProperty("spring.flyway.schemas")).isEqualTo(schema);
		assertThat(this.environment.getProperty("spring.jpa.properties.hibernate.default_schema")).isEqualTo(schema);
		assertThat(this.jdbc.queryForObject("select count(*) from flyway_schema_history where success", Long.class))
				.isPositive();
	}

	@Test
	void disablesLiveMessagingAndStartupBackfills()
	{
		assertThat(this.environment.getProperty("app.messaging.rabbitmq.enabled", Boolean.class)).isFalse();
		assertThat(this.environment.getProperty("app.messaging.rabbitmq.consumer-enabled", Boolean.class)).isFalse();
		assertThat(this.environment.getProperty("app.statistics.backfill-enabled", Boolean.class)).isFalse();
	}
}

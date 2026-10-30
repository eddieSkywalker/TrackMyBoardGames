package com.SwampDragonInn.trackmyboardgames.support;

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;

/**
 * Gives Spring integration tests their own temporary database tables instead of using application records.
 * Spring discovers this test-only setup through META-INF/spring.factories, including when tests run in the IDE.
 */
public class IsolatedTestDatabaseFactory implements ContextCustomizerFactory
{

	@Override
	public ContextCustomizer createContextCustomizer(Class<?> testClass,
			List<ContextConfigurationAttributes> configAttributes)
	{
		if (!AnnotatedElementUtils.hasAnnotation(testClass, SpringBootTest.class))
		{
			return null;
		}
		return new IsolatedDatabase();
	}

	/**
	 * Identical customizers let Spring reuse compatible test contexts; each new context gets a fresh schema.
	 */
	private record IsolatedDatabase() implements ContextCustomizer
	{

		@Override
		public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration configuration)
		{
			// Generate the schema name ourselves so cleanup can only target tables created by this test context.
			String schema = "tmbg_test_" + UUID.randomUUID().toString().replace("-", "");

			// Ignore the application's DB_URL. A different test server must be chosen explicitly with TEST_DB_URL.
			String databaseUrl = "${TEST_DB_URL:jdbc:postgresql://localhost:5432/trackmyboardgames}";
			String databaseUser = "${TEST_DB_USERNAME:${DB_USERNAME:postgres}}";
			String databasePassword = "${TEST_DB_PASSWORD:${DB_PASSWORD:}}";
			TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
					"spring.datasource.url=" + databaseUrl,
					"spring.datasource.username=" + databaseUser,
					"spring.datasource.password=" + databasePassword,
					"spring.datasource.hikari.schema=" + schema,
					"spring.flyway.url=" + databaseUrl,
					"spring.flyway.user=" + databaseUser,
					"spring.flyway.password=" + databasePassword,
					"spring.flyway.default-schema=" + schema,
					"spring.flyway.schemas=" + schema,
					"spring.jpa.properties.hibernate.default_schema=" + schema,
					"app.messaging.rabbitmq.enabled=false",
					"app.messaging.rabbitmq.consumer-enabled=false",
					"app.statistics.backfill-enabled=false");

			// Remove only this context's temporary schema before Spring closes its database connection pool.
			context.addApplicationListener((ContextClosedEvent event) ->
			{
				if (event.getApplicationContext() == context)
				{
					DataSource dataSource = context.getBean(DataSource.class);
					new JdbcTemplate(dataSource).execute("drop schema " + schema + " cascade");
				}
			});
		}
	}
}

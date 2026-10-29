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
 * Upgrades a separate V12 schema with existing records to verify migration preserves identities and images.
 */
@SpringBootTest
class CollectionMigrationTests
{

	@Autowired
	private DataSource dataSource;

	@Test
	void migrationReusesOldestShelfAndPreservesGamesPlaysImagesAndQrCodes()
	{
		String schema = "collection_migration_" + UUID.randomUUID().toString().replace("-", "");
		JdbcTemplate jdbc = new JdbcTemplate(this.dataSource);
		try
		{
			Flyway.configure().dataSource(this.dataSource).defaultSchema(schema).schemas(schema)
					.target("12").load().migrate();
			UUID owner = UUID.randomUUID();
			UUID emptyOwner = UUID.randomUUID();
			UUID shelf = UUID.randomUUID();
			UUID game = UUID.randomUUID();
			UUID play = UUID.randomUUID();
			UUID label = UUID.randomUUID();
			jdbc.update("insert into " + schema + ".app_users values (?, 'issuer', 'owner', current_timestamp)", owner);
			jdbc.update("insert into " + schema + ".app_users values (?, 'issuer', 'empty', current_timestamp)", emptyOwner);
			jdbc.update("insert into " + schema + """
					.game_collections (id, owner_id, name, background_content_type, background_image, created_at)
					values (?, ?, 'Existing shelf', 'image/png', decode('010203', 'hex'), '2026-01-01')
					""", shelf, owner);
			jdbc.update("insert into " + schema + """
					.game_collections (id, owner_id, name, created_at)
					values (?, ?, 'Later shelf', '2026-02-01')
					""", UUID.randomUUID(), owner);
			jdbc.update("insert into " + schema + """
					.board_games (id, owner_id, name, edition, cover_image, created_at, updated_at)
					values (?, ?, 'Azul', '', decode('040506', 'hex'), current_timestamp, current_timestamp)
					""", game, owner);
			jdbc.update("insert into " + schema + """
					.game_plays (id, board_game_id, played_on, created_at, updated_at)
					values (?, ?, '2026-10-01', current_timestamp, current_timestamp)
					""", play, game);
			jdbc.update("insert into " + schema + """
					.qr_label_codes (id, owner_id, board_game_id, token, created_at)
					values (?, ?, ?, 'preserved-token', current_timestamp)
					""", label, owner, game);
			Flyway.configure().dataSource(this.dataSource).defaultSchema(schema).schemas(schema).load().migrate();

			assertThat(jdbc.queryForObject("select collection_id from " + schema + ".board_games where id = ?",
					UUID.class, game)).isEqualTo(shelf);
			assertThat(jdbc.queryForObject("select default_collection_id from " + schema + ".app_users where id = ?",
					UUID.class, owner)).isEqualTo(shelf);
			assertThat(jdbc.queryForObject("select count(*) from " + schema + ".game_collections where owner_id = ?",
					Long.class, emptyOwner)).isEqualTo(1);
			assertThat(jdbc.queryForObject("select board_game_id from " + schema + ".game_plays where id = ?",
					UUID.class, play)).isEqualTo(game);
			assertThat(jdbc.queryForObject("select token from " + schema + ".qr_label_codes where id = ?",
					String.class, label)).isEqualTo("preserved-token");
			assertThat(jdbc.queryForObject("select background_image from " + schema + ".game_collections where id = ?",
					byte[].class, shelf)).containsExactly((byte) 1, (byte) 2, (byte) 3);
			assertThat(jdbc.queryForObject("select cover_image from " + schema + ".board_games where id = ?",
					byte[].class, game)).containsExactly((byte) 4, (byte) 5, (byte) 6);
		}
		finally
		{
			jdbc.execute("drop schema if exists " + schema + " cascade");
		}
	}
}

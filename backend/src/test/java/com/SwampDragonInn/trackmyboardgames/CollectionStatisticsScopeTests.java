package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import com.SwampDragonInn.trackmyboardgames.domain.CollectionStatisticsSummary;
import com.SwampDragonInn.trackmyboardgames.repository.CollectionStatisticsRepository;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsSummaryRepository;
import com.SwampDragonInn.trackmyboardgames.service.StatisticsRefreshService;
import com.SwampDragonInn.trackmyboardgames.service.UserProfileService;

/**
 * Checks separate account and shelf answers, owner privacy, and upgrading existing saved statistics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CollectionStatisticsScopeTests
{

	private static final String ISSUER = "https://example.test/";

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private UserProfileService profiles;

	@Autowired
	private StatisticsRefreshService refresh;

	@Autowired
	private CollectionStatisticsRepository collectionSummaries;

	@Autowired
	private StatisticsSummaryRepository accountSummaries;

	@Autowired
	private MockMvc mvc;

	@Test
	void refreshSavesDifferentWinnersAndCountsForAccountAndEachCollection() throws Exception
	{
		// Give this account two populated shelves and one empty shelf, plus a separate account to exclude.
		String subject = subject();
		UUID owner = owner(subject);
		UUID first = defaultCollection(owner);
		UUID second = collection(owner);
		UUID empty = collection(owner);
		UUID foreignOwner = owner(subject());
		UUID foreignCollection = defaultCollection(foreignOwner);
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		YearMonth month = YearMonth.from(today);

		// Make each populated shelf have its own winner, with plays in this month and the previous month.
		UUID azul = game(owner, first, "Azul", "");
		UUID wingspan = game(owner, second, "Wingspan", "Asia");
		game(owner, second, "Unplayed", "");
		play(azul, today.minusMonths(1));
		play(azul, today);
		play(wingspan, today);
		play(wingspan, today);
		play(wingspan, today);
		play(game(foreignOwner, foreignCollection, "Private", ""), today);
		this.refresh.backfill(foreignOwner);
		Instant foreignRefresh = shelf(foreignCollection, foreignOwner).getRefreshedAt();

		// One event must save both scopes together without refreshing the other account.
		UUID event = UUID.randomUUID();
		assertThat(this.refresh.refresh(event, owner)).isTrue();

		CollectionStatisticsSummary account = this.accountSummaries.findById(owner).orElseThrow();
		assertThat(account.getCollectionSize()).isEqualTo(3);
		assertThat(account.getPlaysForMonth(month)).isEqualTo(4);
		assertThat(account.getMostPlayed().gameId()).isEqualTo(wingspan);
		assertThat(shelf(first, owner).getCollectionSize()).isEqualTo(1);
		assertThat(shelf(first, owner).getMostPlayed().gameId()).isEqualTo(azul);
		assertThat(shelf(first, owner).getPlaysForMonth(month)).isEqualTo(1);
		assertThat(shelf(first, owner).getPlaysForMonth(month.minusMonths(1))).isEqualTo(1);
		assertThat(shelf(second, owner).getCollectionSize()).isEqualTo(2);
		assertThat(shelf(second, owner).getMostPlayed().gameId()).isEqualTo(wingspan);
		assertThat(shelf(second, owner).getMostRecentlyPlayed().edition()).isEqualTo("Asia");
		assertThat(shelf(second, owner).getPlaysForMonth(month)).isEqualTo(3);
		assertThat(shelf(empty, owner).getCollectionSize()).isZero();
		assertThat(shelf(empty, owner).getMostPlayed()).isNull();
		assertThat(shelf(empty, owner).getMostRecentlyPlayed()).isNull();
		assertThat(shelf(empty, owner).getPlaysForMonth(month)).isZero();
		assertThat(shelf(first, owner).getRefreshedAt()).isEqualTo(account.getRefreshedAt());
		assertThat(shelf(second, owner).getRefreshedAt()).isEqualTo(account.getRefreshedAt());
		assertThat(shelf(foreignCollection, foreignOwner).getRefreshedAt()).isEqualTo(foreignRefresh);

		// Changing source data alone must not change either API's saved answers.
		game(owner, first, "Not refreshed yet", "");
		this.mvc.perform(get("/api/statistics").with(token(subject)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.collectionSize").value(3));
		this.mvc.perform(get("/api/statistics").param("collectionId", first.toString()).with(token(subject)))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.collectionSize").value(1))
				.andExpect(jsonPath("$.mostPlayed.gameId").value(azul.toString()))
				.andExpect(jsonPath("$.playsThisMonth").value(1))
				.andExpect(jsonPath("$.month").value(month.toString()))
				.andExpect(jsonPath("$.timezone").value("UTC"))
				.andExpect(jsonPath("$.ownerId").doesNotExist());
	}

	@Test
	void missingSummaryIsPendingButRefreshedEmptyCollectionHasRealZeros() throws Exception
	{
		String subject = subject();
		UUID owner = owner(subject);
		UUID collection = defaultCollection(owner);
		this.mvc.perform(get("/api/statistics").param("collectionId", collection.toString()).with(token(subject)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.collectionSize").value(nullValue()))
				.andExpect(jsonPath("$.playsThisMonth").value(nullValue()))
				.andExpect(jsonPath("$.refreshedAt").value(nullValue()));

		this.refresh.backfill(owner);

		this.mvc.perform(get("/api/statistics").param("collectionId", collection.toString()).with(token(subject)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.collectionSize").value(0))
				.andExpect(jsonPath("$.playsThisMonth").value(0))
				.andExpect(jsonPath("$.mostPlayed").value(nullValue()))
				.andExpect(jsonPath("$.mostRecentlyPlayed").value(nullValue()));
	}

	@Test
	void explicitCollectionRequiresOwnershipAndNeverFallsBackToAccountTotals() throws Exception
	{
		String subject = subject();
		UUID owner = owner(subject);
		UUID foreign = defaultCollection(owner(subject()));
		this.refresh.backfill(owner);

		for (UUID collection : new UUID[] { foreign, UUID.randomUUID() })
		{
			this.mvc.perform(get("/api/statistics").param("collectionId", collection.toString()).with(token(subject)))
					.andExpect(status().isNotFound());
		}
		this.mvc.perform(get("/api/statistics").param("collectionId", defaultCollection(owner).toString())
					.with(token(subject())))
				.andExpect(status().isNotFound());
		this.mvc.perform(get("/api/statistics").param("collectionId", defaultCollection(owner).toString())
					.with(jwt().jwt(builder -> builder.issuer("https://other.test/").subject(subject))
							.authorities(new SimpleGrantedAuthority("SCOPE_read:games"))))
				.andExpect(status().isNotFound());
		this.mvc.perform(get("/api/statistics").param("collectionId", "bad-id").with(token(subject)))
				.andExpect(status().isBadRequest());
		this.mvc.perform(get("/api/statistics").param("collectionId", foreign.toString()))
				.andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/statistics").param("collectionId", foreign.toString()).with(jwt()))
				.andExpect(status().isForbidden());
	}

	@Test
	void editsDeletesAndLateEventsRefreshBothScopesWithoutChangingOtherShelves()
	{
		UUID owner = owner(subject());
		UUID first = defaultCollection(owner);
		UUID second = collection(owner);
		UUID firstGame = game(owner, first, "Azul", "");
		UUID secondGame = game(owner, second, "Wingspan", "");
		UUID firstPlay = play(firstGame, LocalDate.of(2026, 10, 1));
		play(secondGame, LocalDate.of(2026, 10, 2));
		UUID event = UUID.randomUUID();
		this.refresh.refresh(event, owner);

		// A repeated event leaves the saved answers alone, even when source records have changed.
		this.jdbc.update("update board_games set name = 'Renamed' where id = ?", firstGame);
		this.jdbc.update("update game_plays set played_on = '2026-09-01' where id = ?", firstPlay);
		assertThat(this.refresh.refresh(event, owner)).isFalse();
		assertThat(shelf(first, owner).getMostPlayed().name()).isEqualTo("Azul");

		// A new event picks up the rename and date change in the affected shelf.
		this.refresh.refresh(UUID.randomUUID(), owner);
		assertThat(shelf(first, owner).getMostPlayed().name()).isEqualTo("Renamed");
		assertThat(shelf(first, owner).getPlaysForMonth(YearMonth.of(2026, 10))).isZero();
		assertThat(shelf(first, owner).getPlaysForMonth(YearMonth.of(2026, 9))).isEqualTo(1);
		assertThat(shelf(second, owner).getMostPlayed().gameId()).isEqualTo(secondGame);

		// Removed plays and games disappear on the next refresh; deleting an empty shelf removes its saved row.
		this.jdbc.update("delete from game_plays where id = ?", firstPlay);
		this.refresh.refresh(UUID.randomUUID(), owner);
		assertThat(shelf(first, owner).getMostPlayed()).isNull();
		this.jdbc.update("delete from board_games where id = ?", firstGame);
		this.refresh.refresh(UUID.randomUUID(), owner);
		assertThat(shelf(first, owner).getCollectionSize()).isZero();
		assertThat(this.accountSummaries.findById(owner).orElseThrow().getCollectionSize()).isEqualTo(1);
		this.jdbc.update("delete from board_games where id = ?", secondGame);
		this.jdbc.update("delete from game_collections where id = ?", second);
		assertThat(this.collectionSummaries.findByCollectionAndOwner(second, owner)).isEmpty();
		this.refresh.backfill(owner);
		assertThat(this.accountSummaries.findById(owner).orElseThrow().getCollectionSize()).isZero();
	}

	@Test
	void perCollectionTiesUseNameThenEditionAndKeepYearsSeparate()
	{
		UUID owner = owner(subject());
		UUID collection = defaultCollection(owner);
		game(owner, collection, "A unplayed", "");
		UUID winner = game(owner, collection, "azul", "a");
		UUID laterEdition = game(owner, collection, "Azul", "Z");
		UUID laterName = game(owner, collection, "Zebra", "");
		for (UUID game : new UUID[] { winner, laterEdition, laterName })
		{
			play(game, LocalDate.of(2025, 12, 31));
			play(game, LocalDate.of(2026, 1, 1));
		}
		this.refresh.backfill(owner);

		assertThat(shelf(collection, owner).getMostPlayed().gameId()).isEqualTo(winner);
		assertThat(shelf(collection, owner).getMostRecentlyPlayed().gameId()).isEqualTo(winner);
		assertThat(shelf(collection, owner).getPlaysForMonth(YearMonth.of(2025, 12))).isEqualTo(3);
		assertThat(shelf(collection, owner).getPlaysForMonth(YearMonth.of(2026, 1))).isEqualTo(3);
		assertThat(shelf(collection, owner).getPlaysForMonth(YearMonth.of(2027, 1))).isZero();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void migrationInitializesExistingShelvesWithoutLosingAccountAnswers()
	{
		String schema = "scope_migration_" + UUID.randomUUID().toString().replace("-", "");
		try
		{
			// Start at the previous schema with an account, two shelves, and an existing account summary.
			Flyway.configure().dataSource(this.dataSource).defaultSchema(schema).schemas(schema)
					.target("17").load().migrate();
			UUID owner = UUID.randomUUID();
			UUID first = UUID.randomUUID();
			UUID second = UUID.randomUUID();
			this.jdbc.update("insert into " + schema + """
					.app_users (id, auth0_issuer, auth0_subject, created_at)
					values (?, 'issuer', 'migration', current_timestamp)
					""", owner);
			for (UUID collection : new UUID[] { first, second })
			{
				this.jdbc.update("insert into " + schema + """
						.game_collections (id, owner_id, name, created_at)
						values (?, ?, 'Existing shelf', current_timestamp)
						""", collection, owner);
			}
			this.jdbc.update("insert into " + schema + """
					.board_games (id, owner_id, collection_id, name, edition, created_at, updated_at)
					values (gen_random_uuid(), ?, ?, 'Existing game', '', current_timestamp, current_timestamp)
					""", owner, first);
			this.jdbc.update("insert into " + schema + """
					.statistics_summaries (owner_id, collection_size, monthly_plays, refreshed_at)
					values (?, 1, '{}', current_timestamp)
					""", owner);

			// Upgrading creates shelf answers immediately while keeping the account-wide totals.
			Flyway.configure().dataSource(this.dataSource).defaultSchema(schema).schemas(schema).load().migrate();

			assertThat(this.jdbc.queryForObject("select collection_size from " + schema
					+ ".statistics_summaries where owner_id = ?", Long.class, owner)).isEqualTo(1);
			assertThat(this.jdbc.queryForObject("select collection_size from " + schema
					+ ".collection_statistics_summaries where collection_id = ?", Long.class, first)).isEqualTo(1);
			assertThat(this.jdbc.queryForObject("select collection_size from " + schema
					+ ".collection_statistics_summaries where collection_id = ?", Long.class, second)).isZero();
		}
		finally
		{
			this.jdbc.execute("drop schema if exists " + schema + " cascade");
		}
	}

	private UUID owner(String subject)
	{
		return this.profiles.findOrCreate(ISSUER, subject).getId();
	}

	private String subject()
	{
		return "scoped-statistics-" + UUID.randomUUID();
	}

	private UUID defaultCollection(UUID owner)
	{
		return this.jdbc.queryForObject("select default_collection_id from app_users where id = ?", UUID.class, owner);
	}

	private UUID collection(UUID owner)
	{
		UUID id = UUID.randomUUID();
		this.jdbc.update("insert into game_collections (id, owner_id, name, created_at) values (?, ?, 'Shelf', current_timestamp)",
				id, owner);
		return id;
	}

	private UUID game(UUID owner, UUID collection, String name, String edition)
	{
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				insert into board_games (id, owner_id, collection_id, name, edition, created_at, updated_at)
				values (?, ?, ?, ?, ?, current_timestamp, current_timestamp)
				""", id, owner, collection, name, edition);
		return id;
	}

	private UUID play(UUID game, LocalDate date)
	{
		UUID id = UUID.randomUUID();
		this.jdbc.update("insert into game_plays (id, board_game_id, played_on, created_at, updated_at) "
				+ "values (?, ?, ?, current_timestamp, current_timestamp)", id, game, date);
		return id;
	}

	private CollectionStatisticsSummary shelf(UUID collection, UUID owner)
	{
		return this.collectionSummaries.findByCollectionAndOwner(collection, owner).orElseThrow();
	}

	private JwtRequestPostProcessor token(String subject)
	{
		return jwt().jwt(builder -> builder.issuer(ISSUER).subject(subject))
				.authorities(new SimpleGrantedAuthority("SCOPE_read:games"));
	}
}

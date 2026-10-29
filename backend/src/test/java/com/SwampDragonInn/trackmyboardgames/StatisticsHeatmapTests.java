package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.api.MonthlyHeatmapResponse;
import com.SwampDragonInn.trackmyboardgames.repository.StatisticsHeatmapRepository;
import com.SwampDragonInn.trackmyboardgames.service.StatisticsReadService;
import com.SwampDragonInn.trackmyboardgames.service.StatisticsRefreshService;
import com.SwampDragonInn.trackmyboardgames.service.UserProfileService;

/**
 * Checks saved daily answers, scope privacy, changed play history, and upgrading existing accounts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StatisticsHeatmapTests
{

	private static final String ISSUER = "https://example.test/";

	@Autowired private JdbcTemplate jdbc;
	@Autowired private DataSource dataSource;
	@Autowired private UserProfileService profiles;
	@Autowired private StatisticsRefreshService refresh;
	@Autowired private StatisticsReadService statistics;
	@Autowired private StatisticsHeatmapRepository heatmaps;
	@Autowired private MockMvc mvc;

	@Test
	void savedDailyCountsSeparateShelvesAccountsAndCalendarDates() throws Exception
	{
		// Give two shelves different counts on the same day and keep another owner's plays private.
		String subject = subject();
		UUID owner = owner(subject);
		UUID first = defaultCollection(owner);
		UUID second = collection(owner);
		UUID foreignOwner = owner(subject());
		UUID firstGame = game(owner, first);
		UUID secondGame = game(owner, second);
		YearMonth month = YearMonth.now(ZoneOffset.UTC);
		LocalDate date = month.atDay(1);
		play(firstGame, date);
		play(secondGame, date);
		play(secondGame, date);
		play(firstGame, month.minusMonths(1).atEndOfMonth());
		play(firstGame, month.minusYears(1).atDay(1));
		play(game(foreignOwner, defaultCollection(foreignOwner)), date);
		this.refresh.backfill(owner);

		// The API always supplies every date of this month in order, even unplayed dates.
		MonthlyHeatmapResponse all = this.statistics.readMonthlyHeatmap(ISSUER, subject, null);
		assertThat(all.month()).isEqualTo(month.toString());
		assertThat(all.timezone()).isEqualTo("UTC");
		assertThat(all.days()).hasSize(month.lengthOfMonth());
		assertThat(all.days()).extracting(MonthlyHeatmapResponse.Day::date)
				.containsExactlyElementsOf(date.datesUntil(month.plusMonths(1).atDay(1)).toList());
		assertThat(all.days().getFirst().playCount()).isEqualTo(3);
		assertThat(all.days().subList(1, all.days().size())).allMatch(day -> day.playCount() == 0);
		assertThat(this.statistics.readMonthlyHeatmap(ISSUER, subject, first).days().getFirst().playCount()).isEqualTo(1);
		assertThat(this.statistics.readMonthlyHeatmap(ISSUER, subject, second).days().getFirst().playCount()).isEqualTo(2);
		assertThat(this.heatmaps.findSaved(owner, null).orElseThrow().dailyPlays()).hasSize(3);

		this.mvc.perform(get("/api/statistics/heatmap").param("collectionId", second.toString()).with(token(subject)))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.days[0].date").value(date.toString()))
				.andExpect(jsonPath("$.days[0].playCount").value(2))
				.andExpect(jsonPath("$.days.length()").value(month.lengthOfMonth()));
	}

	@Test
	void readsStaySavedUntilRefreshThenDateEditsAndDeletionsReplaceCounts()
	{
		String subject = subject();
		UUID owner = owner(subject);
		UUID collection = defaultCollection(owner);
		UUID game = game(owner, collection);
		LocalDate first = YearMonth.now(ZoneOffset.UTC).atDay(1);
		UUID play = play(game, first);
		UUID event = UUID.randomUUID();
		assertThat(this.refresh.refresh(event, owner)).isTrue();

		// Reading after a source edit still uses the saved answer, and replaying the old event does not recount it.
		this.jdbc.update("update game_plays set played_on = ? where id = ?", first.plusDays(1), play);
		assertThat(this.refresh.refresh(event, owner)).isFalse();
		assertThat(this.statistics.readMonthlyHeatmap(ISSUER, subject, collection).days().getFirst().playCount()).isEqualTo(1);
		this.refresh.refresh(UUID.randomUUID(), owner);
		assertThat(this.statistics.readMonthlyHeatmap(ISSUER, subject, collection).days().getFirst().playCount()).isZero();
		assertThat(this.statistics.readMonthlyHeatmap(ISSUER, subject, null).days().get(1).playCount()).isEqualTo(1);

		// Removing a play, then a game with its plays, removes those saved daily contributions too.
		this.jdbc.update("delete from game_plays where id = ?", play);
		this.refresh.refresh(UUID.randomUUID(), owner);
		assertThat(this.heatmaps.findSaved(owner, collection).orElseThrow().dailyPlays()).isEmpty();
		play(game, first);
		this.refresh.backfill(owner);
		this.jdbc.update("delete from board_games where id = ?", game);
		this.refresh.backfill(owner);
		assertThat(this.heatmaps.findSaved(owner, null).orElseThrow().dailyPlays()).isEmpty();
		assertThat(this.heatmaps.findSaved(owner, collection).orElseThrow().dailyPlays()).isEmpty();
	}

	@Test
	void dailyStorageKeepsLeapDaysAndYearBoundariesDistinct()
	{
		UUID owner = owner(subject());
		UUID collection = defaultCollection(owner);
		UUID game = game(owner, collection);
		play(game, LocalDate.of(2024, 2, 29));
		play(game, LocalDate.of(2025, 12, 31));
		play(game, LocalDate.of(2026, 1, 1));
		this.refresh.backfill(owner);
		assertThat(this.heatmaps.findSaved(owner, collection).orElseThrow().dailyPlays())
				.containsEntry("2024-02-29", 1L).containsEntry("2025-12-31", 1L).containsEntry("2026-01-01", 1L)
				.hasSize(3);
	}

	@Test
	void pendingAndInitializedEmptyMonthsHaveDifferentAnswers() throws Exception
	{
		String subject = subject();
		UUID owner = owner(subject);
		UUID collection = defaultCollection(owner);
		this.mvc.perform(get("/api/statistics/heatmap").with(token(subject)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.days").value(nullValue()))
				.andExpect(jsonPath("$.refreshedAt").value(nullValue()));
		this.mvc.perform(get("/api/statistics/heatmap").param("collectionId", collection.toString()).with(token(subject)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.days").value(nullValue()));
		this.refresh.backfill(owner);
		assertThat(this.statistics.readMonthlyHeatmap(ISSUER, subject, collection).days())
				.hasSize(YearMonth.now(ZoneOffset.UTC).lengthOfMonth()).allMatch(day -> day.playCount() == 0);
		assertThat(this.statistics.readMonthlyHeatmap(ISSUER, subject, null).refreshedAt()).isNotNull();
	}

	@Test
	void checksPermissionsAndOwnershipWithoutCreatingUnknownProfiles() throws Exception
	{
		UUID owner = owner(subject());
		UUID foreign = defaultCollection(owner);
		String unknown = subject();
		this.mvc.perform(get("/api/statistics/heatmap")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/statistics/heatmap").with(jwt())).andExpect(status().isForbidden());
		this.mvc.perform(get("/api/statistics/heatmap").param("collectionId", "bad").with(token(unknown)))
				.andExpect(status().isBadRequest());
		this.mvc.perform(get("/api/statistics/heatmap").param("collectionId", foreign.toString()).with(token(unknown)))
				.andExpect(status().isNotFound());
		String known = subject();
		owner(known);
		this.mvc.perform(get("/api/statistics/heatmap").param("collectionId", foreign.toString()).with(token(known)))
				.andExpect(status().isNotFound());
		this.mvc.perform(get("/api/statistics/heatmap").param("collectionId", UUID.randomUUID().toString()).with(token(known)))
				.andExpect(status().isNotFound());
		this.mvc.perform(get("/api/statistics/heatmap").with(token(unknown)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.days").value(nullValue()));
		assertThat(this.jdbc.queryForObject("select count(*) from app_users where auth0_subject = ?", Long.class, unknown)).isZero();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void migrationFillsExistingDailyAnswersAndPreservesCards()
	{
		String schema = "heatmap_migration_" + UUID.randomUUID().toString().replace("-", "");
		try
		{
			// Start at V18 with real play history and existing account/shelf cards, then apply V19.
			Flyway.configure().dataSource(this.dataSource).defaultSchema(schema).schemas(schema).target("18").load().migrate();
			UUID owner = UUID.randomUUID();
			UUID collection = UUID.randomUUID();
			UUID game = UUID.randomUUID();
			this.jdbc.update("insert into " + schema + ".app_users (id, auth0_issuer, auth0_subject, created_at) values (?, 'issuer', 'migration', current_timestamp)", owner);
			this.jdbc.update("insert into " + schema + ".game_collections (id, owner_id, name, created_at) values (?, ?, 'Shelf', current_timestamp)", collection, owner);
			this.jdbc.update("insert into " + schema + ".board_games (id, owner_id, collection_id, name, edition, created_at, updated_at) values (?, ?, ?, 'Game', '', current_timestamp, current_timestamp)", game, owner, collection);
			this.jdbc.update("insert into " + schema + ".game_plays (id, board_game_id, played_on, created_at, updated_at) values (gen_random_uuid(), ?, '2024-02-29', current_timestamp, current_timestamp)", game);
			this.jdbc.update("insert into " + schema + ".statistics_summaries (owner_id, collection_size, monthly_plays, refreshed_at) values (?, 1, '{}', current_timestamp)", owner);
			this.jdbc.update("insert into " + schema + ".collection_statistics_summaries (collection_id, owner_id, collection_size, monthly_plays, refreshed_at) values (?, ?, 1, '{}', current_timestamp)", collection, owner);
			Flyway.configure().dataSource(this.dataSource).defaultSchema(schema).schemas(schema).load().migrate();
			for (String table : new String[] { "statistics_summaries", "collection_statistics_summaries" })
			{
				assertThat(this.jdbc.queryForObject("select daily_plays ->> '2024-02-29' from " + schema + "." + table + " where owner_id = ?", String.class, owner)).isEqualTo("1");
				assertThat(this.jdbc.queryForObject("select collection_size from " + schema + "." + table + " where owner_id = ?", Long.class, owner)).isEqualTo(1);
			}
		}
		finally
		{
			this.jdbc.execute("drop schema if exists " + schema + " cascade");
		}
	}

	private String subject()
	{
		return "heatmap-" + UUID.randomUUID();
	}

	private UUID owner(String subject)
	{
		return this.profiles.findOrCreate(ISSUER, subject).getId();
	}

	private UUID defaultCollection(UUID owner)
	{
		return this.jdbc.queryForObject("select default_collection_id from app_users where id = ?", UUID.class, owner);
	}

	private UUID collection(UUID owner)
	{
		UUID id = UUID.randomUUID();
		this.jdbc.update("insert into game_collections (id, owner_id, name, created_at) values (?, ?, 'Shelf', current_timestamp)", id, owner);
		return id;
	}

	private UUID game(UUID owner, UUID collection)
	{
		UUID id = UUID.randomUUID();
		this.jdbc.update("insert into board_games (id, owner_id, collection_id, name, edition, created_at, updated_at) values (?, ?, ?, ?, '', current_timestamp, current_timestamp)", id, owner, collection, id.toString());
		return id;
	}

	private UUID play(UUID game, LocalDate date)
	{
		UUID id = UUID.randomUUID();
		this.jdbc.update("insert into game_plays (id, board_game_id, played_on, created_at, updated_at) values (?, ?, ?, current_timestamp, current_timestamp)", id, game, date);
		return id;
	}

	private JwtRequestPostProcessor token(String subject)
	{
		return jwt().jwt(builder -> builder.issuer(ISSUER).subject(subject))
				.authorities(new SimpleGrantedAuthority("SCOPE_read:games"));
	}
}

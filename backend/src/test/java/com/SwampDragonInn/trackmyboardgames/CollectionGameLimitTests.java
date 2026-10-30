package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.api.BoardGameRequest;
import com.SwampDragonInn.trackmyboardgames.service.BoardGameService;
import com.SwampDragonInn.trackmyboardgames.service.CollectionGameLimitException;
import com.SwampDragonInn.trackmyboardgames.service.UserProfileService;

import jakarta.persistence.EntityManager;

/**
 * Uses real request transactions to check game limits, saved events, and competing requests for the last slot.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CollectionGameLimitTests
{

	private static final String ISSUER = "https://example.test/";

	@Autowired
	private BoardGameService games;

	@Autowired
	private UserProfileService profiles;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	private final List<UUID> owners = new ArrayList<>();
	private String subject;
	private UUID ownerId;
	private UUID collectionId;

	@BeforeEach
	void setup()
	{
		this.subject = "game-limits-" + UUID.randomUUID();
		var owner = this.profiles.findOrCreate(ISSUER, this.subject);
		this.ownerId = owner.getId();
		this.collectionId = owner.getDefaultCollection().getId();
		this.owners.add(this.ownerId);
	}

	@AfterEach
	void cleanup()
	{
		for (UUID owner : this.owners)
		{
			this.jdbc.update("delete from outbox_events where owner_id = ?", owner);
			this.jdbc.update("delete from app_users where id = ?", owner);
		}
	}

	@ParameterizedTest
	@CsvSource({ "FREE,20", "HOME,100", "PRO,200", "BUSINESS,1000" })
	void lastSlotIsAllowedAndNextGameIsRejectedForEveryTier(String tier, int limit)
	{
		setTier(tier);
		seedGames(this.collectionId, limit - 1);

		this.games.create(ISSUER, this.subject, request("Last slot"), this.collectionId);

		assertThatThrownBy(() -> this.games.create(ISSUER, this.subject, request("One too many"), this.collectionId))
				.isInstanceOf(CollectionGameLimitException.class)
				.hasMessageContaining("limit of " + limit);
		assertThat(gameCount(this.collectionId)).isEqualTo(limit);
		assertThat(eventCount()).isEqualTo(1);
	}

	@Test
	void apiReturnsReadableConflictAndDefaultCollectionCannotBypassLimit() throws Exception
	{
		seedGames(this.collectionId, 20);

		this.mockMvc.perform(post("/api/games")
					.with(jwt().jwt(token -> token.issuer(ISSUER).subject(this.subject)
							.claim("scope", "write:games")))
					.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Blocked\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("This collection has reached its limit of 20 games. "
						+ "Remove a game or upgrade the collection before adding another."));

		assertThat(gameCount(this.collectionId)).isEqualTo(20);
		assertThat(eventCount()).isZero();
	}

	@ParameterizedTest
	@CsvSource({ "FREE,20", "HOME,100", "PRO,200" })
	void expiredBusinessUsesEarnedTierAndNeverDeletesExistingGames(String permanentTier, int limit)
	{
		setTier(permanentTier);
		this.jdbc.update("""
				update game_collections set plan_tier = 'BUSINESS', subscription_status = 'ACTIVE',
				subscription_ends_at = current_timestamp - interval '1 second' where id = ?
				""", this.collectionId);
		seedGames(this.collectionId, limit + 1);

		assertThatThrownBy(() -> this.games.create(ISSUER, this.subject, request("Expired access")))
				.isInstanceOf(CollectionGameLimitException.class)
				.hasMessageContaining("limit of " + limit);
		assertThat(gameCount(this.collectionId)).isEqualTo(limit + 1);
		assertThat(eventCount()).isZero();
	}

	@Test
	void inactiveBusinessFallsBackToFreeEvenWithFutureEnd()
	{
		this.jdbc.update("""
				update game_collections set plan_tier = 'BUSINESS', subscription_status = 'INACTIVE',
				subscription_ends_at = current_timestamp + interval '1 day' where id = ?
				""", this.collectionId);
		seedGames(this.collectionId, 20);

		assertThatThrownBy(() -> this.games.create(ISSUER, this.subject, request("Inactive access")))
				.isInstanceOf(CollectionGameLimitException.class).hasMessageContaining("limit of 20");
	}

	@Test
	@Transactional
	void newSavesUseChangedDatabaseLimitsRatherThanTheOriginalDefaults()
	{
		// This new limit list rolls back with the test so other tests still use the original allowances.
		this.jdbc.update("""
				insert into collection_limit_versions
				(effective_from, free_limit, home_limit, pro_limit, business_limit, change_reason)
				values (current_timestamp, 3, 5, 7, 9, 'Check new-game enforcement')
				""");
		seedGames(this.collectionId, 2);
		this.games.create(ISSUER, this.subject, request("Third game"));
		this.entityManager.flush();

		assertThatThrownBy(() -> this.games.create(ISSUER, this.subject, request("Fourth game")))
				.isInstanceOf(CollectionGameLimitException.class).hasMessageContaining("limit of 3");
		assertThat(gameCount(this.collectionId)).isEqualTo(3);
		assertThat(eventCount()).isEqualTo(1);
	}

	@Test
	void fullCollectionsDoNotUseUpOtherCollectionsOrOwnersAllowances()
	{
		seedGames(this.collectionId, 20);
		UUID extra = UUID.randomUUID();
		this.jdbc.update("""
				insert into game_collections (id, owner_id, name, created_at)
				values (?, ?, 'Separate collection', current_timestamp)
				""", extra, this.ownerId);
		this.games.create(ISSUER, this.subject, request("Separate slot"), extra);

		String otherSubject = "other-limits-" + UUID.randomUUID();
		var other = this.profiles.findOrCreate(ISSUER, otherSubject);
		this.owners.add(other.getId());
		this.games.create(ISSUER, otherSubject, request("Another owner"));

		assertThat(gameCount(this.collectionId)).isEqualTo(20);
		assertThat(gameCount(extra)).isEqualTo(1);
		assertThat(gameCount(other.getDefaultCollection().getId())).isEqualTo(1);
	}

	@Test
	void editsAndDeletesStillWorkAtTheLimitAndDeletingMakesRoom()
	{
		seedGames(this.collectionId, 20);
		UUID gameId = this.jdbc.queryForObject("select id from board_games where collection_id = ? limit 1",
				UUID.class, this.collectionId);

		this.games.update(ISSUER, this.subject, gameId, request("Updated while full"));
		this.games.delete(ISSUER, this.subject, gameId);
		this.games.create(ISSUER, this.subject, request("Replacement"));

		assertThat(gameCount(this.collectionId)).isEqualTo(20);
		assertThat(eventCount()).isEqualTo(3);
	}

	@Test
	void competingRequestsCanOnlyFillTheLastSlotOnce() throws Exception
	{
		seedGames(this.collectionId, 19);
		CountDownLatch ready = new CountDownLatch(3);
		CountDownLatch start = new CountDownLatch(1);

		// Start three independent transactions together, with only one game slot remaining.
		try (var executor = Executors.newFixedThreadPool(3))
		{
			List<java.util.concurrent.Future<Boolean>> results = new ArrayList<>();
			for (int index = 0; index < 3; index++)
			{
				String name = "Competing game " + index;
				results.add(executor.submit(() ->
				{
					ready.countDown();
					if (!start.await(10, TimeUnit.SECONDS))
					{
						throw new IllegalStateException("Timed out waiting to start game requests.");
					}

					try
					{
						this.games.create(ISSUER, this.subject, request(name));
						return true;
					}
					catch (CollectionGameLimitException expected)
					{
						return false;
					}
				}));
			}

			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			int saved = 0;
			for (var result : results)
			{
				if (result.get(20, TimeUnit.SECONDS))
				{
					saved++;
				}
			}

			assertThat(saved).isEqualTo(1);
		}
		finally
		{
			start.countDown();
		}

		assertThat(gameCount(this.collectionId)).isEqualTo(20);
		assertThat(eventCount()).isEqualTo(1);
	}

	private void setTier(String tier)
	{
		String permanentTier = "BUSINESS".equals(tier) ? "FREE" : tier;
		this.jdbc.update("update game_collections set plan_tier = ?, permanent_tier = ? where id = ?",
				permanentTier, permanentTier, this.collectionId);

		if ("BUSINESS".equals(tier))
		{
			this.jdbc.update("""
					update game_collections set plan_tier = 'BUSINESS', subscription_status = 'ACTIVE',
					subscription_ends_at = current_timestamp + interval '1 day' where id = ?
					""", this.collectionId);
		}
	}

	/**
	 * Adds existing games directly so the test can reach a boundary without hundreds of API requests.
	 */
	private void seedGames(UUID collection, int count)
	{
		this.jdbc.update("""
				insert into board_games (id, owner_id, collection_id, name, edition, created_at, updated_at)
				select gen_random_uuid(), ?, ?, ? || number, '', current_timestamp, current_timestamp
				from generate_series(1, ?) number
				""", this.ownerId, collection, "Existing " + collection + " ", count);
	}

	private long gameCount(UUID collection)
	{
		return this.jdbc.queryForObject("select count(*) from board_games where collection_id = ?", Long.class, collection);
	}

	private long eventCount()
	{
		return this.jdbc.queryForObject("select count(*) from outbox_events where owner_id = ?", Long.class, this.ownerId);
	}

	private BoardGameRequest request(String name)
	{
		return new BoardGameRequest(name, null, null, null, null, null, null, null);
	}
}

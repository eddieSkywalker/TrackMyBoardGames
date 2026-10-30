package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;
import com.SwampDragonInn.trackmyboardgames.service.GameCollectionService;
import com.SwampDragonInn.trackmyboardgames.service.UserProfileService;

import jakarta.persistence.EntityManager;

/**
 * Verifies starting-collection initialization and ownership constraints without enabling collection creation.
 */
@SpringBootTest
class CollectionFoundationTests
{

	@Autowired
	private UserProfileService profiles;

	@Autowired
	private GameCollectionService collections;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactions;

	@Test
	void concurrentFirstRequestsCreateExactlyOneProfileAndStartingCollection() throws Exception
	{
		String subject = "foundation-" + UUID.randomUUID();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2))
		{
			var first = executor.submit(() -> initialize(subject, ready, start));
			var second = executor.submit(() -> initialize(subject, ready, start));
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			UUID ownerId = first.get(20, TimeUnit.SECONDS);
			assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(ownerId);
			assertThat(this.jdbc.queryForObject(
					"select count(*) from game_collections where owner_id = ?", Long.class, ownerId)).isEqualTo(1);
			assertThat(this.jdbc.queryForObject(
					"select default_collection_id from app_users where id = ?", UUID.class, ownerId)).isNotNull();
		}
		finally
		{
			start.countDown();
			this.jdbc.update("delete from app_users where auth0_issuer = ? and auth0_subject = ?",
					"https://example.test/", subject);
		}
	}

	@Test
	void newGamesUseTheStartingCollectionAndShelfReadsDoNotCreateAnother()
	{
		String subject = "foundation-" + UUID.randomUUID();
		try
		{
			UUID gameId = new TransactionTemplate(this.transactions).execute(status ->
			{
				AppUser owner = this.profiles.findOrCreate("https://example.test/", subject);
				BoardGame game = new BoardGame(owner, "Azul", "", null, null, null, null, null);
				this.entityManager.persist(game);
				assertThat(game.getCollection().getOwner().getId()).isEqualTo(owner.getId());
				return game.getId();
			});
			assertThat(this.collections.getBackgroundImage("https://example.test/", subject)).isNull();
			assertThat(this.jdbc.queryForObject("""
					select count(*) from board_games g join app_users u on u.id = g.owner_id
					where g.id = ? and g.collection_id = u.default_collection_id
					""", Long.class, gameId)).isEqualTo(1);
			assertThat(this.jdbc.queryForObject("""
					select count(*) from game_collections c join app_users u on u.id = c.owner_id
					where u.auth0_subject = ?
					""", Long.class, subject)).isEqualTo(1);
		}
		finally
		{
			this.jdbc.update("delete from app_users where auth0_subject = ?", subject);
		}
	}

	@Test
	void databaseRejectsAnotherOwnersCollectionForGamesAndDefaultPreference()
	{
		String first = "foundation-" + UUID.randomUUID();
		String second = "foundation-" + UUID.randomUUID();
		try
		{
			UUID firstId = this.profiles.findOrCreate("https://example.test/", first).getId();
			UUID secondId = this.profiles.findOrCreate("https://example.test/", second).getId();
			UUID secondCollection = this.jdbc.queryForObject(
					"select default_collection_id from app_users where id = ?", UUID.class, secondId);
			assertThatThrownBy(() -> this.jdbc.update(
					"update app_users set default_collection_id = ? where id = ?", secondCollection, firstId))
					.isInstanceOf(DataIntegrityViolationException.class);
			assertThatThrownBy(() -> this.jdbc.update("""
					insert into board_games (id, owner_id, collection_id, name, edition, created_at, updated_at)
					values (?, ?, ?, 'Invalid owner', '', current_timestamp, current_timestamp)
					""", UUID.randomUUID(), firstId, secondCollection))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
		finally
		{
			this.jdbc.update("delete from app_users where auth0_subject in (?, ?)", first, second);
		}
	}

	private UUID initialize(String subject, CountDownLatch ready, CountDownLatch start) throws InterruptedException
	{
		ready.countDown();
		if (!start.await(10, TimeUnit.SECONDS))
		{
			throw new IllegalStateException("Timed out waiting to start concurrent profile initialization.");
		}
		return this.profiles.findOrCreate("https://example.test/", subject).getId();
	}
}

package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.SwampDragonInn.trackmyboardgames.service.CollectionPaymentService;
import com.SwampDragonInn.trackmyboardgames.service.CollectionPaymentService.ConfirmedPayment;
import com.SwampDragonInn.trackmyboardgames.service.UserProfileService;

/**
 * Uses real transactions to check confirmed-payment totals, duplicate delivery, and concurrent monthly payments.
 */
@SpringBootTest
class CollectionPaymentTests
{

	private static final String ISSUER = "https://example.test/";
	private static final Instant PAID_AT = Instant.parse("2026-10-29T16:00:00Z");
	private final List<UUID> owners = new ArrayList<>();

	@Autowired
	private CollectionPaymentService payments;

	@Autowired
	private UserProfileService profiles;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactions;

	@AfterEach
	void cleanup()
	{
		for (UUID owner : this.owners)
		{
			this.jdbc.update("delete from app_users where id = ?", owner);
		}
	}

	@Test
	void twoMonthlyPaymentsEarnProAndAccountTotalsDoNotTransferCreditBetweenCollections()
	{
		UUID owner = newOwner();
		UUID first = startingCollection(owner);
		UUID second = extraCollection(owner);

		this.payments.recordConfirmedPayment(month(owner, first, "first", "period-1", 3000));
		assertThat(value(first, "permanent_tier", String.class)).isEqualTo("HOME");
		assertThat(value(first, "subscriber_months", Long.class)).isEqualTo(1L);
		this.payments.recordConfirmedPayment(month(owner, first, "second", "period-2", 3000));
		assertThat(value(first, "permanent_tier", String.class)).isEqualTo("PRO");
		assertThat(value(first, "subscriber_months", Long.class)).isEqualTo(2L);

		this.payments.recordConfirmedPayment(oneTime(owner, second, "third", 1000));
		assertThat(value(second, "permanent_tier", String.class)).isEqualTo("FREE");
		assertThat(value(second, "subscriber_months", Long.class)).isZero();
		assertThat(this.payments.totalPaymentsForOwner(owner)).isEqualTo(7000);
	}

	@Test
	void duplicateReceiptsDoNotAddPaymentsOrPaidMonthsAgain()
	{
		UUID owner = newOwner();
		UUID collection = startingCollection(owner);
		ConfirmedPayment payment = month(owner, collection, "duplicate", "period-1", 3000);

		this.payments.recordConfirmedPayment(payment);
		this.payments.recordConfirmedPayment(payment);

		assertThat(value(collection, "total_payments", Long.class)).isEqualTo(3000L);
		assertThat(value(collection, "subscriber_months", Long.class)).isEqualTo(1L);
		assertThatThrownBy(() -> this.payments.recordConfirmedPayment(
				month(owner, collection, "duplicate", "period-1", 4000))).isInstanceOf(IllegalArgumentException.class);
		assertThat(this.payments.totalPaymentsForOwner(owner)).isEqualTo(3000);
	}

	@Test
	void secondReceiptForTheSameMonthlyPeriodIsRejectedWithoutChangingTotals()
	{
		UUID owner = newOwner();
		UUID collection = startingCollection(owner);
		this.payments.recordConfirmedPayment(month(owner, collection, "receipt-1", "same-period", 3000));

		assertThatThrownBy(() -> this.payments.recordConfirmedPayment(
				month(owner, collection, "receipt-2", "same-period", 3000)))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(value(collection, "subscriber_months", Long.class)).isEqualTo(1L);
		assertThat(this.payments.totalPaymentsForOwner(owner)).isEqualTo(3000);
	}

	@Test
	void failureRollsBackPaymentReceiptTotalsMonthsAndTierTogether()
	{
		UUID owner = newOwner();
		UUID collection = startingCollection(owner);
		ConfirmedPayment payment = month(owner, collection, "rollback", "period-1", 6000);

		assertThatThrownBy(() -> new TransactionTemplate(this.transactions).executeWithoutResult(status ->
		{
			this.payments.recordConfirmedPayment(payment);
			throw new IllegalStateException("Simulated failure.");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(this.payments.totalPaymentsForOwner(owner)).isZero();
		assertThat(value(collection, "total_payments", Long.class)).isZero();
		assertThat(value(collection, "subscriber_months", Long.class)).isZero();
		assertThat(value(collection, "plan_tier", String.class)).isEqualTo("FREE");
		this.payments.recordConfirmedPayment(payment);
		assertThat(value(collection, "permanent_tier", String.class)).isEqualTo("PRO");
	}

	@Test
	void concurrentMonthlyPaymentsDoNotLoseTotalsOrDoubleCount() throws Exception
	{
		UUID owner = newOwner();
		UUID collection = startingCollection(owner);
		CountDownLatch ready = new CountDownLatch(3);
		CountDownLatch start = new CountDownLatch(1);
		List<ConfirmedPayment> receipts = List.of(
				month(owner, collection, "concurrent-1", "period-1", 3000),
				month(owner, collection, "concurrent-2", "period-2", 3000),
				month(owner, collection, "concurrent-1", "period-1", 3000));

		try (var executor = Executors.newFixedThreadPool(3))
		{
			var tasks = receipts.stream().map(receipt -> executor.submit(() ->
			{
				ready.countDown();
				if (!start.await(10, TimeUnit.SECONDS))
				{
					throw new IllegalStateException("Timed out waiting for concurrent payments.");
				}
				this.payments.recordConfirmedPayment(receipt);
				return true;
			})).toList();

			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			for (var task : tasks)
			{
				assertThat(task.get(20, TimeUnit.SECONDS)).isTrue();
			}
		}
		finally
		{
			start.countDown();
		}

		assertThat(value(collection, "total_payments", Long.class)).isEqualTo(6000L);
		assertThat(value(collection, "subscriber_months", Long.class)).isEqualTo(2L);
		assertThat(value(collection, "permanent_tier", String.class)).isEqualTo("PRO");
	}

	@Test
	void collectionDeletionKeepsAccountPaymentHistoryAndOwnerDeletionCleansItUp()
	{
		UUID owner = newOwner();
		UUID extra = extraCollection(owner);
		this.payments.recordConfirmedPayment(oneTime(owner, extra, "history", 5000));
		this.jdbc.update("delete from game_collections where id = ?", extra);

		assertThat(this.payments.totalPaymentsForOwner(owner)).isEqualTo(5000);
		assertThat(this.jdbc.queryForObject(
				"select count(*) from collection_payments where owner_id = ? and collection_id is null", Long.class, owner))
				.isEqualTo(1);
		this.jdbc.update("delete from app_users where id = ?", owner);
		assertThat(this.payments.totalPaymentsForOwner(owner)).isZero();
	}

	@Test
	void wrongOwnerAndInvalidAmountsCurrencyOrPeriodsCannotRecordPayments()
	{
		UUID owner = newOwner();
		UUID other = newOwner();
		UUID collection = startingCollection(owner);

		assertThatThrownBy(() -> this.payments.recordConfirmedPayment(oneTime(other, collection, "foreign", 3000)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> oneTime(owner, collection, "zero", 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ConfirmedPayment(owner, collection, "currency", 3000, "EUR", PAID_AT, null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ConfirmedPayment(owner, collection, "period", 3000, "USD", PAID_AT, "month", null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ConfirmedPayment(owner, collection, "period", 3000, "USD", PAID_AT, "month", PAID_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(this.payments.totalPaymentsForOwner(owner)).isZero();
	}

	@Test
	void receiptReferenceCannotBeReusedAcrossCollectionsOrOwners()
	{
		UUID owner = newOwner();
		UUID other = newOwner();
		UUID first = startingCollection(owner);
		UUID second = extraCollection(owner);
		ConfirmedPayment original = oneTime(owner, first, "global-reference", 3000);
		this.payments.recordConfirmedPayment(original);

		assertThatThrownBy(() -> this.payments.recordConfirmedPayment(new ConfirmedPayment(
				owner, second, original.uniquePaymentId(), 3000, "USD", PAID_AT, null, null)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> this.payments.recordConfirmedPayment(new ConfirmedPayment(
				other, startingCollection(other), original.uniquePaymentId(), 3000, "USD", PAID_AT, null, null)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(this.payments.totalPaymentsForOwner(owner)).isEqualTo(3000);
		assertThat(this.payments.totalPaymentsForOwner(other)).isZero();
	}

	@Test
	void databaseRejectsNegativeTotalsAndIncompleteBusinessPeriodRecords()
	{
		UUID owner = newOwner();
		UUID collection = startingCollection(owner);

		assertThatThrownBy(() -> this.jdbc.update("update game_collections set total_payments = -1 where id = ?", collection))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.jdbc.update("update game_collections set subscriber_months = -1 where id = ?", collection))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.jdbc.update("""
				insert into collection_payments
				(id, owner_id, collection_id, payment_reference, amount_cents, currency, paid_at,
				business_period_reference, price_version_id)
				values (?, ?, ?, ?, 3000, 'USD', current_timestamp, 'period-without-end',
					(select id from collection_price_versions where effective_from <= current_timestamp
						order by effective_from desc limit 1))
				""", UUID.randomUUID(), owner, collection, "invalid-" + owner))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_collection_paid_period");
		assertThat(this.payments.totalPaymentsForOwner(owner)).isZero();
	}

	private UUID newOwner()
	{
		UUID owner = this.profiles.findOrCreate(ISSUER, "payments-" + UUID.randomUUID()).getId();
		this.owners.add(owner);
		return owner;
	}

	private UUID startingCollection(UUID owner)
	{
		return this.jdbc.queryForObject("select default_collection_id from app_users where id = ?", UUID.class, owner);
	}

	private UUID extraCollection(UUID owner)
	{
		UUID collection = UUID.randomUUID();
		this.jdbc.update("""
				insert into game_collections (id, owner_id, name, created_at) values (?, ?, 'Extra', current_timestamp)
				""", collection, owner);
		return collection;
	}

	private ConfirmedPayment month(UUID owner, UUID collection, String uniquePaymentId, String businessSubscriptionMonthId, long amount)
	{
		return new ConfirmedPayment(owner, collection, owner + uniquePaymentId, amount, "USD", PAID_AT, businessSubscriptionMonthId,
				PAID_AT.plusSeconds(30 * 24 * 60 * 60));
	}

	private ConfirmedPayment oneTime(UUID owner, UUID collection, String uniquePaymentId, long amount)
	{
		return new ConfirmedPayment(owner, collection, owner + uniquePaymentId, amount, "USD", PAID_AT, null, null);
	}

	private <T> T value(UUID collection, String column, Class<T> type)
	{
		return this.jdbc.queryForObject("select " + column + " from game_collections where id = ?", type, collection);
	}
}

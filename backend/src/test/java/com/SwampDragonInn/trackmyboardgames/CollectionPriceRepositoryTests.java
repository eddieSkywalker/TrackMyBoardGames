package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.domain.CollectionTier;
import com.SwampDragonInn.trackmyboardgames.repository.CollectionPriceRepository;
import com.SwampDragonInn.trackmyboardgames.service.CollectionPaymentService;
import com.SwampDragonInn.trackmyboardgames.service.CollectionPaymentService.ConfirmedPayment;
import com.SwampDragonInn.trackmyboardgames.service.CollectionPrices;
import com.SwampDragonInn.trackmyboardgames.service.UserProfileService;

/**
 * Checks saved price history and payment pricing using a database transaction that rolls back after each test.
 */
@SpringBootTest
@Transactional
class CollectionPriceRepositoryTests
{

	@Autowired
	private CollectionPriceRepository prices;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CollectionPaymentService payments;

	@Autowired
	private UserProfileService profiles;

	@Test
	void latestStartedPricesAreLoadedAndFuturePricesWait()
	{
		UUID currentVersion = insertPrices("current_timestamp", 4000, 2000, 3500);
		insertPrices("current_timestamp + interval '1 day'", 6000, 9000, 4500);

		assertThat(this.prices.findCurrent().id()).isEqualTo(currentVersion);
		assertThat(this.prices.findCurrent().prices()).isEqualTo(new CollectionPrices(4000, 2000, 3500));
		assertThat(this.prices.findCurrent().prices().earnedTier(2000)).isEqualTo(CollectionTier.PRO);
	}

	@Test
	void newPaymentsUseNewPricesAndRepeatedPaymentsKeepTheirOriginalVersion()
	{
		UUID originalVersion = insertPrices("current_timestamp - interval '1 microsecond'", 3000, 5000, 3000);
		var owner = this.profiles.findOrCreate("https://example.test/", "price-version-" + UUID.randomUUID());
		UUID collectionId = owner.getDefaultCollection().getId();
		Instant paidAt = this.jdbc.queryForObject("select current_timestamp",
				java.sql.Timestamp.class).toInstant();
		ConfirmedPayment first = new ConfirmedPayment(owner.getId(), collectionId, UUID.randomUUID().toString(),
				3000, "USD", paidAt, null, null);
		this.payments.recordConfirmedPayment(first);

		UUID nextVersion = insertPrices("current_timestamp", 8000, 10000, 3500);
		this.payments.recordConfirmedPayment(first);
		ConfirmedPayment second = new ConfirmedPayment(owner.getId(), collectionId, UUID.randomUUID().toString(),
				2000, "USD", paidAt, null, null);
		this.payments.recordConfirmedPayment(second);

		assertThat(paymentPriceVersion(first.uniquePaymentId())).isEqualTo(originalVersion);
		assertThat(paymentPriceVersion(second.uniquePaymentId())).isEqualTo(nextVersion);
		assertThat(this.payments.totalPaymentsForOwner(owner.getId())).isEqualTo(5000);
		// $50 would have earned Pro under the old prices, but the new Pro threshold is $100.
		assertThat(this.jdbc.queryForObject("select permanent_tier from game_collections where id = ?",
				String.class, collectionId)).isEqualTo("HOME");
	}

	@Test
	void newPaymentsMustIncludeThePriceVersionUsed()
	{
		var owner = this.profiles.findOrCreate("https://example.test/", "missing-price-version-" + UUID.randomUUID());

		assertThatThrownBy(() -> this.jdbc.update("""
				insert into collection_payments
				(id, owner_id, collection_id, payment_reference, amount_cents, currency, paid_at)
				values (?, ?, ?, ?, 3000, 'USD', current_timestamp)
				""", UUID.randomUUID(), owner.getId(), owner.getDefaultCollection().getId(), UUID.randomUUID().toString()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("New payments must identify the price version used");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"update collection_price_versions set home_cents = 1",
			"delete from collection_price_versions",
			"truncate collection_price_versions cascade",
			"insert into collection_price_versions (effective_from, home_cents, pro_cents, business_monthly_cents, change_reason) "
					+ "values (current_timestamp, 0, 5000, 3000, 'Invalid price')",
			"insert into collection_price_versions (effective_from, home_cents, pro_cents, business_monthly_cents, change_reason) "
					+ "values (current_timestamp, 3000, -1, 3000, 'Invalid price')",
			"insert into collection_price_versions (effective_from, home_cents, pro_cents, business_monthly_cents, change_reason) "
					+ "values (current_timestamp, 3000, 5000, 0, 'Invalid price')",
			"insert into collection_price_versions (effective_from, home_cents, pro_cents, business_monthly_cents, change_reason) "
					+ "values (current_timestamp, 3000, 5000, 3000, ' ')",
			"insert into collection_price_versions (effective_from, home_cents, pro_cents, business_monthly_cents, currency, change_reason) "
					+ "values (current_timestamp, 3000, 5000, 3000, 'EUR', 'Unsupported currency')"
	})
	void databaseRejectsPriceRewritesAndInvalidNewPrices(String sql)
	{
		assertThatThrownBy(() -> this.jdbc.execute(sql)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private UUID insertPrices(String startDateSql, long home, long pro, long business)
	{
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				insert into collection_price_versions
				(id, effective_from, home_cents, pro_cents, business_monthly_cents, change_reason)
				values (?, %s, ?, ?, ?, 'Price history test')
				""".formatted(startDateSql), id, home, pro, business);
		return id;
	}

	private UUID paymentPriceVersion(String uniquePaymentId)
	{
		return this.jdbc.queryForObject("select price_version_id from collection_payments where payment_reference = ?",
				UUID.class, uniquePaymentId);
	}
}

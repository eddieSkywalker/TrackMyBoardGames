package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.CollectionSubscriptionStatus;
import com.SwampDragonInn.trackmyboardgames.domain.CollectionTier;
import com.SwampDragonInn.trackmyboardgames.domain.GameCollection;
import com.SwampDragonInn.trackmyboardgames.repository.CollectionPriceRepository;
import com.SwampDragonInn.trackmyboardgames.repository.CollectionLimitRepository;
import com.SwampDragonInn.trackmyboardgames.repository.CollectionPriceRepository.PriceVersion;
import com.SwampDragonInn.trackmyboardgames.repository.GameCollectionRepository;
import com.SwampDragonInn.trackmyboardgames.service.CollectionAccessPolicy;
import com.SwampDragonInn.trackmyboardgames.service.CollectionPaymentService;
import com.SwampDragonInn.trackmyboardgames.service.CollectionPaymentService.ConfirmedPayment;
import com.SwampDragonInn.trackmyboardgames.service.CollectionPrices;
import com.SwampDragonInn.trackmyboardgames.service.CollectionTierLimits;

import jakarta.persistence.EntityManager;

/**
 * Checks payment-service rules, price thresholds, and subscription boundaries with mocked persistence.
 */
class CollectionAccessPolicyTests
{

	private static final Instant NOW = Instant.parse("2026-10-29T04:00:00Z");
	private final CollectionAccessPolicy policy = policyWithLimits(new CollectionTierLimits(20, 100, 200, 1000));
	private final CollectionPrices prices = new CollectionPrices(3000, 5000, 3000);

	@Test
	void pricesUnlockPermanentAccessAtTheExactThreshold()
	{
		assertThat(this.prices.earnedTier(2999)).isEqualTo(CollectionTier.FREE);
		assertThat(this.prices.earnedTier(3000)).isEqualTo(CollectionTier.HOME);
		assertThat(this.prices.earnedTier(4999)).isEqualTo(CollectionTier.HOME);
		assertThat(this.prices.earnedTier(5000)).isEqualTo(CollectionTier.PRO);
		assertThat(this.prices.earnedTier(6000)).isEqualTo(CollectionTier.PRO);
	}

	@Test
	void businessExpiresToTheTierActuallyEarnedRatherThanAutomaticallyPro()
	{
		GameCollection collection = collection();
		Instant expiry = NOW.plusSeconds(60);
		recordPayment(collection, 3000, this.prices, expiry);

		assertThat(this.policy.effectiveTier(collection, expiry.minusNanos(1))).isEqualTo(CollectionTier.BUSINESS);
		assertThat(this.policy.gameLimit(collection, expiry.minusNanos(1))).isEqualTo(1000);
		assertThat(this.policy.effectiveTier(collection, expiry)).isEqualTo(CollectionTier.HOME);
		assertThat(this.policy.gameLimit(collection, expiry)).isEqualTo(100);
		assertThat(collection.getSubscriberMonths()).isEqualTo(1);

		recordPayment(collection, 3000, this.prices, expiry.plusSeconds(60));

		assertThat(collection.getSubscriberMonths()).isEqualTo(2);
		assertThat(collection.getTotalPayments()).isEqualTo(6000);
		assertThat(this.policy.effectiveTier(collection, expiry.plusSeconds(60))).isEqualTo(CollectionTier.PRO);
		assertThat(collection.getPlanTier()).isEqualTo(CollectionTier.BUSINESS);
	}

	@Test
	void subscriptionCanFallBackToFreeWhenPaymentsHaveNotReachedHome()
	{
		GameCollection collection = collection();
		recordPayment(collection, 1000, this.prices, NOW.plusSeconds(60));

		assertThat(this.policy.effectiveTier(collection, NOW.plusSeconds(60))).isEqualTo(CollectionTier.FREE);
	}

	@Test
	void earnedAccessSurvivesPriceIncreasesAndUpgradeQuotesUseOnlyTheRemainingAmount()
	{
		GameCollection collection = collection();
		recordPayment(collection, 5000, this.prices, null);
		CollectionPrices higherPrices = new CollectionPrices(4000, 8000, 3000);
		recordPayment(collection, 100, higherPrices, null);

		assertThat(collection.getPermanentTier()).isEqualTo(CollectionTier.PRO);
		assertThat(this.policy.effectiveTier(collection, NOW)).isEqualTo(CollectionTier.PRO);
		assertThat(higherPrices.remainingCost(CollectionTier.PRO, collection.getPermanentTier(), 5100)).isZero();
		assertThat(this.prices.remainingCost(CollectionTier.PRO, CollectionTier.HOME, 3000)).isEqualTo(2000);
		assertThat(this.prices.remainingCost(CollectionTier.HOME, CollectionTier.FREE, 1000)).isEqualTo(2000);
		assertThat(this.prices.remainingCost(CollectionTier.BUSINESS, CollectionTier.PRO, 9000)).isEqualTo(3000);
	}

	@Test
	void historicalMonthlyPaymentsDoNotShortenNewerBusinessAccess()
	{
		GameCollection collection = collection();
		recordPayment(collection, 3000, this.prices, NOW.plusSeconds(120));
		recordPayment(collection, 3000, this.prices, NOW.plusSeconds(60));

		assertThat(collection.getSubscriptionEndsAt()).isEqualTo(NOW.plusSeconds(120));
		assertThat(collection.getSubscriberMonths()).isEqualTo(2);
		assertThat(this.policy.effectiveTier(collection, NOW.plusSeconds(90))).isEqualTo(CollectionTier.BUSINESS);
	}

	@Test
	void oneTimeUpgradeKeepsExistingBusinessSubscriptionAndPaidMonthCount()
	{
		GameCollection collection = collection();
		Instant expiry = NOW.plusSeconds(60);
		recordPayment(collection, 3000, this.prices, expiry);
		recordPayment(collection, 2000, this.prices, null);

		assertThat(collection.getTotalPayments()).isEqualTo(5000);
		assertThat(collection.getPermanentTier()).isEqualTo(CollectionTier.PRO);
		assertThat(collection.getPlanTier()).isEqualTo(CollectionTier.BUSINESS);
		assertThat(collection.getSubscriptionStatus()).isEqualTo(CollectionSubscriptionStatus.ACTIVE);
		assertThat(collection.getSubscriptionEndsAt()).isEqualTo(expiry);
		assertThat(collection.getSubscriberMonths()).isEqualTo(1);
		assertThat(this.policy.effectiveTier(collection, expiry)).isEqualTo(CollectionTier.PRO);
	}

	@Test
	void businessRequiresPlanStatusAndFutureEndTime()
	{
		GameCollection collection = collection();
		recordPayment(collection, 3000, this.prices, NOW.plusSeconds(60));
		collection.setSubscriptionStatus(CollectionSubscriptionStatus.INACTIVE);
		assertThat(this.policy.effectiveTier(collection, NOW)).isEqualTo(CollectionTier.HOME);

		collection.setSubscriptionStatus(CollectionSubscriptionStatus.ACTIVE);
		collection.setPlanTier(CollectionTier.HOME);
		assertThat(this.policy.effectiveTier(collection, NOW)).isEqualTo(CollectionTier.HOME);

		collection.setPlanTier(CollectionTier.BUSINESS);
		collection.setSubscriptionEndsAt(null);
		assertThat(this.policy.effectiveTier(collection, NOW)).isEqualTo(CollectionTier.HOME);
	}

	@Test
	void freeAccessAndConfiguredAllowancesRemainSupported()
	{
		CollectionAccessPolicy configured = policyWithLimits(new CollectionTierLimits(3, 5, 7, 9));
		GameCollection collection = collection();
		assertThat(configured.gameLimit(collection, NOW)).isEqualTo(3);
		recordPayment(collection, 3000, this.prices, null);
		assertThat(configured.gameLimit(collection, NOW)).isEqualTo(5);
		assertThat(collection.getPlanTier()).isEqualTo(CollectionTier.HOME);
		recordPayment(collection, 2000, this.prices, null);
		assertThat(configured.gameLimit(collection, NOW)).isEqualTo(7);
		assertThat(collection.getPlanTier()).isEqualTo(CollectionTier.PRO);
		recordPayment(collection, 3000, this.prices, NOW.plusSeconds(60));
		assertThat(configured.gameLimit(collection, NOW)).isEqualTo(9);
	}

	@Test
	void invalidPaymentCannotChangeCollectionTotals()
	{
		GameCollection collection = collection();
		assertThatThrownBy(() -> recordPayment(collection, 0, this.prices, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(collection.getTotalPayments()).isZero();
		assertThat(collection.getPermanentTier()).isEqualTo(CollectionTier.FREE);
	}

	/**
	 * Runs the real payment workflow while treating each receipt as newly inserted.
	 * Database transaction, duplicate, and locking behavior is covered by CollectionPaymentTests.
	 */
	private void recordPayment(GameCollection collection, long amountCents, CollectionPrices prices, Instant businessEndsAt)
	{
		UUID ownerId = UUID.randomUUID();
		UUID collectionId = UUID.randomUUID();
		String uniquePaymentId = UUID.randomUUID().toString();
		ConfirmedPayment payment = new ConfirmedPayment(ownerId, collectionId, uniquePaymentId, amountCents, "USD", NOW,
				businessEndsAt == null ? null : uniquePaymentId, businessEndsAt);
		GameCollectionRepository collections = mock(GameCollectionRepository.class);
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		CollectionPriceRepository priceRepository = mock(CollectionPriceRepository.class);

		when(collections.findForPayment(collectionId, ownerId)).thenReturn(Optional.of(collection));
		when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
		when(priceRepository.findCurrent()).thenReturn(new PriceVersion(UUID.randomUUID(), prices));

		CollectionPaymentService payments = new CollectionPaymentService(collections, mock(EntityManager.class), jdbc, priceRepository);
		payments.recordConfirmedPayment(payment);
	}

	private GameCollection collection()
	{
		return new GameCollection(new AppUser("https://example.test/", "access-test"), "Access test");
	}

	private CollectionAccessPolicy policyWithLimits(CollectionTierLimits limits)
	{
		CollectionLimitRepository repository = mock(CollectionLimitRepository.class);
		when(repository.findCurrent()).thenReturn(limits);
		return new CollectionAccessPolicy(repository);
	}
}

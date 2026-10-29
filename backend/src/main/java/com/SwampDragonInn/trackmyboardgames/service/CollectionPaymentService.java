package com.SwampDragonInn.trackmyboardgames.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.domain.CollectionSubscriptionStatus;
import com.SwampDragonInn.trackmyboardgames.domain.CollectionTier;
import com.SwampDragonInn.trackmyboardgames.domain.GameCollection;
import com.SwampDragonInn.trackmyboardgames.repository.CollectionPriceRepository;
import com.SwampDragonInn.trackmyboardgames.repository.CollectionPriceRepository.PriceVersion;
import com.SwampDragonInn.trackmyboardgames.repository.GameCollectionRepository;

import jakarta.persistence.EntityManager;

/**
 * Saves completed payments and updates the collection's total paid, paid Business months, and earned tier.
 * Payment history and collection changes are saved together in one database transaction.
 */
@Service
public class CollectionPaymentService
{

	private final GameCollectionRepository collections;
	private final EntityManager entityManager;
	private final JdbcTemplate jdbc;
	private final CollectionPriceRepository priceRepository;

	/**
	 * Supplies the database tools and tier prices used to record payments and update collections.
	 */
	public CollectionPaymentService(GameCollectionRepository collections, EntityManager entityManager,
			JdbcTemplate jdbc, CollectionPriceRepository priceRepository)
	{
		this.collections = collections;
		this.entityManager = entityManager;
		this.jdbc = jdbc;
		this.priceRepository = priceRepository;
	}

	/**
	 * Holds the details of one completed payment for a collection.
	 * A Business payment also identifies the subscription month it pays for and when that month's access ends.
	 *
	 * @param ownerId the account that made the payment
	 * @param collectionId the collection receiving the payment credit
	 * @param uniquePaymentId the billing system's unique payment ID; use the same ID whenever reporting that payment again
	 * @param amountCents the amount paid in whole cents, such as 3000 for $30
	 * @param currency the payment currency, currently USD
	 * @param paidAt when the payment completed
	 * @param businessSubscriptionMonthId the billing system's ID for the paid subscription month, or null for a one-time payment
	 * @param businessAccessEndsAt when access for that paid month ends, or null for a one-time payment
	 */
	public record ConfirmedPayment(UUID ownerId, UUID collectionId, String uniquePaymentId, long amountCents,
			String currency, Instant paidAt, String businessSubscriptionMonthId, Instant businessAccessEndsAt)
	{
		/**
		 * Checks that the payment includes the account, collection, amount, and dates needed to record it.
		 */
		public ConfirmedPayment
		{
			// Every payment must identify who paid, which collection gets credit, and when it was paid.
			Objects.requireNonNull(ownerId, "A payment owner is required.");
			Objects.requireNonNull(collectionId, "A payment collection is required.");
			Objects.requireNonNull(paidAt, "The confirmed payment time is required.");

			// Require a usable payment ID and a positive amount in the supported currency.
			if (uniquePaymentId == null || uniquePaymentId.isBlank() || uniquePaymentId.length() > 200 || amountCents <= 0
					|| !"USD".equals(currency))
			{
				throw new IllegalArgumentException("Payments require a unique payment ID, a positive amount in cents, and USD currency.");
			}

			boolean hasBusinessPeriod = businessSubscriptionMonthId != null;

			// Business payments need both a paid-month ID and an access end date; one-time payments need neither.
			// The month ID must fit storage, and its access must end after the payment time.
			if (hasBusinessPeriod != (businessAccessEndsAt != null)
					|| (hasBusinessPeriod && (businessSubscriptionMonthId.isBlank() || businessSubscriptionMonthId.length() > 200
							|| !businessAccessEndsAt.isAfter(paidAt))))
			{
				throw new IllegalArgumentException("Business payments require a subscription month ID and an end after payment.");
			}
		}
	}

	/**
	 * Saves a completed payment and applies its credit to the collection once.
	 * If the same payment ID arrives again, matching details are accepted without adding credit again.
	 * Different details under an already saved payment ID are rejected.
	 */
	@Transactional
	public void recordConfirmedPayment(ConfirmedPayment payment)
	{
		// Find this account's collection and lock it until this transaction finishes.
        // Other payments and game additions for the same collection wait their turn.
		GameCollection collection = this.collections.findForUpdate(payment.collectionId(), payment.ownerId())
				.orElseThrow(() -> new IllegalArgumentException("The collection was not found for this account."));

		// Reload its latest values in case another payment finished while this request waited for the lock.
		this.entityManager.refresh(collection);

		// Load one saved price list and keep its ID with the payment so its pricing can be checked later.
		PriceVersion priceVersion = this.priceRepository.findCurrent();

		// Save the payment history. An already saved payment ID leaves the existing row unchanged.
		int inserted = this.jdbc.update("""
				insert into collection_payments
				(id, owner_id, collection_id, payment_reference, amount_cents, currency, paid_at,
				business_period_reference, business_access_ends_at, price_version_id)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				on conflict (payment_reference) do nothing
				""", UUID.randomUUID(), payment.ownerId(), payment.collectionId(), payment.uniquePaymentId(), payment.amountCents(),
				payment.currency(), java.sql.Timestamp.from(payment.paidAt()), payment.businessSubscriptionMonthId(),
				payment.businessAccessEndsAt() == null ? null : java.sql.Timestamp.from(payment.businessAccessEndsAt()),
				priceVersion.id());

		if (inserted == 0)
		{
			// Check that this is the same payment, then stop before counting its money or paid month again.
			verifyRepeatedPayment(payment);
			return;
		}

		// Apply the new payment and send the collection changes to the database within the same transaction.
		applyPaymentToCollection(collection, payment, priceVersion.prices());
		this.entityManager.flush();
	}

	/**
	 * Adds a newly recorded payment to the collection's totals and updates its permanent tier and Business access.
	 * Once a permanent tier is earned, later payments preserve it even if prices have increased.
	 */
	private void applyPaymentToCollection(GameCollection collection, ConfirmedPayment payment, CollectionPrices prices)
	{
		// Add this payment to the collection's lifetime total and check which permanent tier that total earns.
		long qualifyingTotal = Math.addExact(collection.getTotalPayments(), payment.amountCents());
		CollectionTier earnedTier = prices.earnedTier(qualifyingTotal);
		collection.setTotalPayments(qualifyingTotal);

		// Upgrade permanent access when earned, while keeping any higher tier the collection already has.
		if (earnedTier == CollectionTier.PRO
				|| (earnedTier == CollectionTier.HOME && collection.getPermanentTier() == CollectionTier.FREE))
		{
			collection.setPermanentTier(earnedTier);
		}

		Instant businessEndsAt = payment.businessAccessEndsAt();

		if (businessEndsAt != null)
		{
			// Count the paid Business month and mark the collection as subscribed.
			collection.setSubscriberMonths(Math.addExact(collection.getSubscriberMonths(), 1));
			collection.setSubscriptionStatus(CollectionSubscriptionStatus.ACTIVE);
			collection.setPlanTier(CollectionTier.BUSINESS);

			// A late report of an older payment must not shorten access from a newer paid month.
			if (collection.getSubscriptionEndsAt() == null || businessEndsAt.isAfter(collection.getSubscriptionEndsAt()))
			{
				collection.setSubscriptionEndsAt(businessEndsAt);
			}
		}
		else if (collection.getPlanTier() != CollectionTier.BUSINESS)
		{
			// A one-time payment updates the plan to the earned tier unless the collection is on Business.
			collection.setPlanTier(collection.getPermanentTier());
		}
	}

	/**
	 * Returns the account's total paid in USD cents across all collections, including deleted collections.
	 */
	@Transactional(readOnly = true)
	public long totalPaymentsForOwner(UUID ownerId)
	{
		// Add the account's saved payments, returning zero when there are no payments.
		return this.jdbc.queryForObject("""
				select coalesce(sum(amount_cents), 0) from collection_payments where owner_id = ?
				""", Long.class, ownerId);
	}

	/**
	 * Checks that a repeated payment ID has exactly the same details as the saved payment.
	 */
	private void verifyRepeatedPayment(ConfirmedPayment payment)
	{
		// Compare every payment detail, using the database's date precision and treating two missing values as equal.
		Long matches = this.jdbc.queryForObject("""
				select count(*) from collection_payments
				where payment_reference = ? and owner_id = ? and collection_id = ?
				and amount_cents = ? and currency = ? and paid_at = cast(? as timestamptz)
				and business_period_reference is not distinct from cast(? as varchar)
				and business_access_ends_at is not distinct from cast(? as timestamptz)
				""", Long.class, payment.uniquePaymentId(), payment.ownerId(), payment.collectionId(), payment.amountCents(),
				payment.currency(), java.sql.Timestamp.from(payment.paidAt()), payment.businessSubscriptionMonthId(),
				payment.businessAccessEndsAt() == null ? null : java.sql.Timestamp.from(payment.businessAccessEndsAt()));

		// Reject a reused payment ID if it describes a different payment, owner, or collection.
		if (matches == null || matches != 1)
		{
			throw new IllegalArgumentException("This payment ID was already recorded with different details.");
		}
	}
}

package com.SwampDragonInn.trackmyboardgames.service;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.SwampDragonInn.trackmyboardgames.domain.CollectionSubscriptionStatus;
import com.SwampDragonInn.trackmyboardgames.domain.CollectionTier;
import com.SwampDragonInn.trackmyboardgames.domain.GameCollection;
import com.SwampDragonInn.trackmyboardgames.repository.CollectionLimitRepository;

/**
 * Answers what a collection may use now, even if a background task has not processed an expired subscription.
 * Checking access does not delete games or change saved records.
 */
@Service
public class CollectionAccessPolicy
{

	private final CollectionLimitRepository limits;

	/**
	 * Supplies access to the game limits saved in the database.
	 */
	public CollectionAccessPolicy(CollectionLimitRepository limits)
	{
		this.limits = limits;
	}

	/**
	 * Business needs an active subscription with time remaining; otherwise permanent access applies.
	 * At the exact end time, Business access has ended.
	 */
	public CollectionTier effectiveTier(GameCollection collection, Instant now)
	{
		Objects.requireNonNull(now, "The current time is required.");
		Instant endsAt = collection.getSubscriptionEndsAt();

		boolean hasBusinessAccess = collection.getPlanTier() == CollectionTier.BUSINESS
				&& collection.getSubscriptionStatus() == CollectionSubscriptionStatus.ACTIVE
				&& endsAt != null && endsAt.isAfter(now);

		if (hasBusinessAccess)
		{
			return CollectionTier.BUSINESS;
		}

		return collection.getPermanentTier();
	}

	/**
	 * Returns the allowance for the access that is valid now, not merely the last saved plan label.
	 */
	public int gameLimit(GameCollection collection, Instant now)
	{
		return this.limits.findCurrent().forTier(effectiveTier(collection, now));
	}
}

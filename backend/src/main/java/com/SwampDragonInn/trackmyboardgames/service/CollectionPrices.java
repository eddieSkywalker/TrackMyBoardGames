package com.SwampDragonInn.trackmyboardgames.service;

import com.SwampDragonInn.trackmyboardgames.domain.CollectionTier;

/**
 * Holds one saved price list and calculates earned tiers and remaining upgrade costs.
 * All values are whole USD cents: 3000 means $30.
 */
public record CollectionPrices(long home, long pro, long businessMonthly)
{

	/**
	 * Requires a positive amount for each price, with no price ordering between tiers.
	 */
	public CollectionPrices
	{
		if (home <= 0 || pro <= 0 || businessMonthly <= 0)
		{
			throw new IllegalArgumentException("Each collection price must be greater than zero.");
		}
	}

	/**
	 * Finds the permanent tier earned by this collection's qualifying payments, including exact price matches.
	 */
	public CollectionTier earnedTier(long qualifyingTotal)
	{
		if (qualifyingTotal >= pro)
		{
			return CollectionTier.PRO;
		}

		if (qualifyingTotal >= home)
		{
			return CollectionTier.HOME;
		}

		return CollectionTier.FREE;
	}

	/**
	 * Calculates an upgrade's remaining cost without charging again for a tier already earned.
	 * Monthly Business charges are separate recurring prices, not permanent upgrade thresholds.
	 */
	public long remainingCost(CollectionTier target, CollectionTier permanentTier, long qualifyingTotal)
	{
		if (qualifyingTotal < 0)
		{
			throw new IllegalArgumentException("Qualifying payments cannot be negative.");
		}

		return switch (target)
		{
			case FREE -> 0;
			case HOME -> permanentTier == CollectionTier.FREE ? Math.max(0, home - qualifyingTotal) : 0;
			case PRO -> permanentTier == CollectionTier.PRO ? 0 : Math.max(0, pro - qualifyingTotal);
			case BUSINESS -> businessMonthly;
		};
	}
}

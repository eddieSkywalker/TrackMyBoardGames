package com.SwampDragonInn.trackmyboardgames.service;

import com.SwampDragonInn.trackmyboardgames.domain.CollectionTier;

/**
 * Holds one saved set of maximum game counts for the collection tiers.
 */
public record CollectionTierLimits(int free, int home, int pro, int business)
{

	/**
	 * Requires a positive game limit for every tier.
	 */
	public CollectionTierLimits
	{
		if (free <= 0 || home <= 0 || pro <= 0 || business <= 0)
		{
			throw new IllegalArgumentException("Each collection game limit must be greater than zero.");
		}
	}

	/**
	 * Returns the saved maximum number of games for the supplied tier.
	 */
	public int forTier(CollectionTier tier)
	{
		return switch (tier)
		{
			case FREE -> free;
			case HOME -> home;
			case PRO -> pro;
			case BUSINESS -> business;
		};
	}
}

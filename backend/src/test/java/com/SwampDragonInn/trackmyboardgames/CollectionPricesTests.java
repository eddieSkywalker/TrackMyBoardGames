package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.SwampDragonInn.trackmyboardgames.domain.CollectionTier;
import com.SwampDragonInn.trackmyboardgames.repository.CollectionPriceRepository;
import com.SwampDragonInn.trackmyboardgames.repository.CollectionPriceRepository.PriceVersion;
import com.SwampDragonInn.trackmyboardgames.service.CollectionPrices;

/**
 * Checks independent tier prices and upgrade calculations without database access.
 */
class CollectionPricesTests
{

	@Test
	void missingCurrentPricesFailInsteadOfUsingDefaults()
	{
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<PriceVersion>>any()))
				.thenReturn(List.of());

		assertThatThrownBy(() -> new CollectionPriceRepository(jdbc).findCurrent())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("No collection prices");
	}

	@Test
	void pricesRemainSeparateFromGameLimits()
	{
		CollectionPrices prices = new CollectionPrices(3000, 5000, 3000);

		assertThat(prices.home()).isEqualTo(3000);
		assertThat(prices.pro()).isEqualTo(5000);
		assertThat(prices.businessMonthly()).isEqualTo(3000);
	}

	@Test
	void proCanCostLessThanHomeAndUsesItsOwnPaymentThreshold()
	{
		CollectionPrices prices = new CollectionPrices(3000, 2000, 3000);

		assertThat(prices.earnedTier(1999)).isEqualTo(CollectionTier.FREE);
		assertThat(prices.earnedTier(2000)).isEqualTo(CollectionTier.PRO);
		assertThat(prices.remainingCost(CollectionTier.PRO, CollectionTier.FREE, 1500)).isEqualTo(500);
	}

	@Test
	void nonpositivePricesAreRejected()
	{
		for (long invalid : new long[] { 0, -1 })
		{
			assertThatThrownBy(() -> new CollectionPrices(invalid, 5000, 3000))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new CollectionPrices(3000, invalid, 3000))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new CollectionPrices(3000, 5000, invalid))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}
}

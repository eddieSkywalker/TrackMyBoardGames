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
import com.SwampDragonInn.trackmyboardgames.repository.CollectionLimitRepository;
import com.SwampDragonInn.trackmyboardgames.service.CollectionTierLimits;

/**
 * Checks saved game limits and rejects missing or invalid values.
 */
class CollectionTierLimitsTests
{

	@Test
	void savedLimitsApplyToEveryTier()
	{
		CollectionTierLimits limits = new CollectionTierLimits(20, 100, 200, 1000);
		assertThat(limits.forTier(CollectionTier.FREE)).isEqualTo(20);
		assertThat(limits.forTier(CollectionTier.HOME)).isEqualTo(100);
		assertThat(limits.forTier(CollectionTier.PRO)).isEqualTo(200);
		assertThat(limits.forTier(CollectionTier.BUSINESS)).isEqualTo(1000);
	}

	@Test
	void zeroAndNegativeLimitsAreRejected()
	{
		for (int invalid : new int[] { 0, -1 })
		{
			assertThatThrownBy(() -> new CollectionTierLimits(invalid, 100, 200, 1000))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new CollectionTierLimits(20, invalid, 200, 1000))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new CollectionTierLimits(20, 100, invalid, 1000))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new CollectionTierLimits(20, 100, 200, invalid))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void missingSavedLimitsFailInsteadOfUsingDefaults()
	{
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<CollectionTierLimits>>any()))
				.thenReturn(List.of());

		assertThatThrownBy(() -> new CollectionLimitRepository(jdbc).findCurrent())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("No collection game limits");
	}
}

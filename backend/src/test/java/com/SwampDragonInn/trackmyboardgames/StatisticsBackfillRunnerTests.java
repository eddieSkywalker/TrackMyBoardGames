package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.PageRequest;

import com.SwampDragonInn.trackmyboardgames.repository.AppUserRepository;
import com.SwampDragonInn.trackmyboardgames.service.StatisticsBackfillRunner;
import com.SwampDragonInn.trackmyboardgames.service.StatisticsRefreshService;

class StatisticsBackfillRunnerTests
{

	private final AppUserRepository users = mock(AppUserRepository.class);
	private final StatisticsRefreshService statistics = mock(StatisticsRefreshService.class);

	@Test
	void processesEachPageAndContinuesPastDeletedOwners()
	{
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		UUID third = UUID.randomUUID();
		var page = PageRequest.of(0, 100);
		when(this.users.findIdsForStatisticsBackfill(null, page)).thenReturn(List.of(first, second));
		when(this.users.findIdsForStatisticsBackfill(second, page)).thenReturn(List.of(third));
		when(this.users.findIdsForStatisticsBackfill(third, page)).thenReturn(List.of());
		when(this.statistics.backfill(first)).thenReturn(true);
		when(this.statistics.backfill(second)).thenReturn(false);
		when(this.statistics.backfill(third)).thenReturn(true);

		new StatisticsBackfillRunner(this.users, this.statistics).run(new DefaultApplicationArguments());

		verify(this.statistics).backfill(first);
		verify(this.statistics).backfill(second);
		verify(this.statistics).backfill(third);
		verifyNoMoreInteractions(this.statistics);
	}

	@Test
	void failureStopsRunInsteadOfReportingSuccess()
	{
		UUID owner = UUID.randomUUID();
		when(this.users.findIdsForStatisticsBackfill(null, PageRequest.of(0, 100))).thenReturn(List.of(owner));
		when(this.statistics.backfill(owner)).thenThrow(new IllegalStateException("Database unavailable"));

		assertThatThrownBy(() -> new StatisticsBackfillRunner(this.users, this.statistics)
				.run(new DefaultApplicationArguments()))
				.isInstanceOf(IllegalStateException.class).hasMessage("Database unavailable");
		verify(this.users, times(1)).findIdsForStatisticsBackfill(any(), any());
	}

	@Test
	void startupJobIsAbsentUnlessExplicitlyEnabled()
	{
		var context = new ApplicationContextRunner()
				.withBean(AppUserRepository.class, () -> this.users)
				.withBean(StatisticsRefreshService.class, () -> this.statistics)
				.withUserConfiguration(StatisticsBackfillRunner.class);
		context.run(result -> assertThat(result)
				.doesNotHaveBean(StatisticsBackfillRunner.class));
		context.withPropertyValues("app.statistics.backfill-enabled=true")
				.run(result -> assertThat(result)
						.hasSingleBean(StatisticsBackfillRunner.class));
		verifyNoInteractions(this.users, this.statistics);
	}
}

package com.SwampDragonInn.trackmyboardgames.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.SwampDragonInn.trackmyboardgames.repository.AppUserRepository;

// Optional startup job that builds statistics from existing collections. It does not create games,
// plays, or RabbitMQ messages. Leave it disabled during normal starts; enable it for a controlled run.
@Component
@ConditionalOnProperty(prefix = "app.statistics", name = "backfill-enabled", havingValue = "true")
public class StatisticsBackfillRunner implements ApplicationRunner
{

	private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsBackfillRunner.class);
	private static final int PAGE_SIZE = 100;

	private final AppUserRepository users;
	private final StatisticsRefreshService statistics;

	// Calls a separate Spring service so each user's backfill gets its own database transaction.
	public StatisticsBackfillRunner(AppUserRepository users, StatisticsRefreshService statistics)
	{
		this.users = users;
		this.statistics = statistics;
	}

	// Reads IDs in small pages instead of loading every account at once. A failed calculation stops
	// startup with an error; earlier users stay committed, and rerunning safely recalculates them.
	@Override
	public void run(ApplicationArguments arguments)
	{
		LOGGER.info("Starting statistics backfill for existing users.");
		UUID afterId = null;
		long completed = 0;
		long skipped = 0;
		while (true)
		{
			List<UUID> ownerIds = this.users.findIdsForStatisticsBackfill(afterId, PageRequest.of(0, PAGE_SIZE));
			if (ownerIds.isEmpty())
			{
				break;
			}
			for (UUID ownerId : ownerIds)
			{
				if (this.statistics.backfill(ownerId))
				{
					completed++;
				}
				else
				{
					skipped++;
				}
			}
			afterId = ownerIds.getLast();
			LOGGER.info("Statistics backfill progress: {} users completed, {} deleted accounts skipped.", completed, skipped);
		}
		LOGGER.info("Statistics backfill finished: {} users completed, {} deleted accounts skipped.", completed, skipped);
	}
}

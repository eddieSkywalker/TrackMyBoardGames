package com.SwampDragonInn.trackmyboardgames.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SwampDragonInn.trackmyboardgames.domain.StatisticsSummary;

/**
 * Reads and saves calculated results in {@code statistics_summaries}, with one row per owner.
 *
 * <p>The statistics refresh service saves the calculated answers here. The read service fetches those
 * saved answers when React requests statistics, without recalculating them.
 *
 * <p>This interface needs no custom queries: {@code JpaRepository} already supplies methods such as
 * {@code saveAndFlush(summary)} and {@code findById(ownerId)}. Spring Data creates their implementation.
 */
public interface StatisticsSummaryRepository extends JpaRepository<StatisticsSummary, UUID>
{
}

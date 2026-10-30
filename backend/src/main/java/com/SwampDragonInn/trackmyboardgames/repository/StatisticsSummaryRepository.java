package com.SwampDragonInn.trackmyboardgames.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SwampDragonInn.trackmyboardgames.domain.CollectionStatisticsSummary;

/**
 * Reads and saves calculated results in {@code statistics_summaries}, with one row per owner.
 *
 * <p>The read service fetches saved answers when React requests statistics, without recalculating them.
 * The refresh workflow writes the same table through the {@code refresh_collection_statistics}
 * PostgreSQL procedure, not through this repository.
 *
 * <p>This interface needs no custom queries: {@code JpaRepository} already supplies methods such as
 * {@code findById(ownerId)}. Spring Data creates their implementation.
 */
public interface StatisticsSummaryRepository extends JpaRepository<CollectionStatisticsSummary, UUID>
{
}

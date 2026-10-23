package com.SwampDragonInn.trackmyboardgames.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SwampDragonInn.trackmyboardgames.domain.StatisticsSummary;

// Reads and saves the background-calculated statistics, with one row per owner.
public interface StatisticsSummaryRepository extends JpaRepository<StatisticsSummary, UUID>
{
}

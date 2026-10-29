package com.SwampDragonInn.trackmyboardgames.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;

/**
 * Calls database procedures for the RabbitMQ-triggered statistics refresh workflow.
 *
 * <p>StatisticsRefreshService calls these queries after the RabbitMQ consumer receives a game or play
 * change event. The same procedure also supports startup backfill without a RabbitMQ message.
 * PostgreSQL calculates the statistics and saves the summary within the service's transaction.
 *
 * <p>This repository is not used by StatisticsReadService to fulfill frontend statistics requests.
 * It does not retrieve saved summaries or record event receipts.
 */
public interface StatisticsRefreshQueryRepository extends Repository<BoardGame, UUID>
{

	/**
	 * Recalculates and saves one owner's collection summary in PostgreSQL.
	 *
	 * <p>The caller must hold the owner lock and supply the existing transaction.
	 * The procedure does not commit independently or return calculated data to Java.
	 *
	 * @param ownerId ID of the user whose games to summarize
	 * @param refreshedAt timestamp shared with the event receipt when processing an event
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "call refresh_collection_statistics(:ownerId, :refreshedAt)", nativeQuery = true)
	void refreshCollectionStatistics(@Param("ownerId") UUID ownerId, @Param("refreshedAt") Instant refreshedAt);
}

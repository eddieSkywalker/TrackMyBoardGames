package com.SwampDragonInn.trackmyboardgames.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;

/**
 * Reads current game and play records to supply totals for statistics calculations.
 *
 * <p>The statistics refresh service uses these queries during event processing and startup backfill.
 * They return small sets of totals for one owner, not game images or private notes. This repository
 * does not save calculated statistics or track which RabbitMQ events have been processed.
 */
public interface StatisticsSourceRepository extends Repository<BoardGame, UUID>
{

	interface GameTotals
	{
		UUID getGameId();
		String getName();
		String getEdition();
		long getPlayCount();
		LocalDate getLastPlayedOn();
	}

	interface MonthTotal
	{
		String getMonth();
		long getPlayCount();
	}

	/**
	 * Loads each game's play count and most recent played-on date for one owner.
	 *
	 * @param ownerId ID of the user whose games to summarize
	 * @return totals for every owned game, including unplayed games so they count toward collection size
	 */
	@Query("""
			select g.id as gameId, g.name as name, g.edition as edition,
				count(p.id) as playCount, max(p.playedOn) as lastPlayedOn
			from BoardGame g left join GamePlay p on p.boardGame.id = g.id
			where g.owner.id = :ownerId
			group by g.id, g.name, g.edition
			""")
	List<GameTotals> findGameTotals(@Param("ownerId") UUID ownerId);

	/**
	 * Counts an owner's plays by the year and month of their played-on dates.
	 *
	 * @param ownerId ID of the user whose plays to count
	 * @return monthly totals with year-month keys, keeping January 2026 separate from January 2027
	 */
	@Query(value = """
			select to_char(p.played_on, 'YYYY-MM') as month, count(*) as "playCount"
			from game_plays p join board_games g on g.id = p.board_game_id
			where g.owner_id = :ownerId
			group by to_char(p.played_on, 'YYYY-MM')
			""", nativeQuery = true)
	List<MonthTotal> findMonthlyTotals(@Param("ownerId") UUID ownerId);
}

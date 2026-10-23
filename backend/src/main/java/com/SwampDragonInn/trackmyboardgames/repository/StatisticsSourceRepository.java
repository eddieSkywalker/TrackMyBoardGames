package com.SwampDragonInn.trackmyboardgames.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;

// Counts the original records only during a background refresh. These queries select small summaries,
// not game images or private notes, and always limit results to one owner.
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

	// Includes unplayed games so they still count toward collection size.
	@Query("""
			select g.id as gameId, g.name as name, g.edition as edition,
				count(p.id) as playCount, max(p.playedOn) as lastPlayedOn
			from BoardGame g left join GamePlay p on p.boardGame.id = g.id
			where g.owner.id = :ownerId
			group by g.id, g.name, g.edition
			""")
	List<GameTotals> findGameTotals(@Param("ownerId") UUID ownerId);

	// Uses the date the game was played, and keeps years separate (January 2026 is not January 2027).
	@Query(value = """
			select to_char(p.played_on, 'YYYY-MM') as month, count(*) as "playCount"
			from game_plays p join board_games g on g.id = p.board_game_id
			where g.owner_id = :ownerId
			group by to_char(p.played_on, 'YYYY-MM')
			""", nativeQuery = true)
	List<MonthTotal> findMonthlyTotals(@Param("ownerId") UUID ownerId);
}

package com.SwampDragonInn.trackmyboardgames.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.SwampDragonInn.trackmyboardgames.domain.GamePlay;

// Database access for plays; every query includes the game and its owner so users only reach their own plays.
public interface GamePlayRepository extends JpaRepository<GamePlay, UUID>
{

	// One row of totals for a game: how many plays it has and when it was last played.
	interface PlayStats
	{
		UUID getGameId();

		long getPlayCount();

		LocalDate getLastPlayedOn();
	}

	List<GamePlay> findAllByBoardGame_IdAndBoardGame_Owner_IdOrderByPlayedOnDescCreatedAtDesc(UUID boardGameId, UUID ownerId);

	Optional<GamePlay> findByIdAndBoardGame_IdAndBoardGame_Owner_Id(UUID id, UUID boardGameId, UUID ownerId);

	// Totals for every game the owner has played, in one query so the list page avoids one query per game.
	// Games with no plays have no row here; callers treat a missing row as zero plays.
	@Query("""
			select p.boardGame.id as gameId, count(p) as playCount, max(p.playedOn) as lastPlayedOn
			from GamePlay p
			where p.boardGame.owner.id = :ownerId
			group by p.boardGame.id
			""")
	List<PlayStats> findPlayStatsByOwner(@Param("ownerId") UUID ownerId);

	// Same totals for a single game (used when returning one game).
	@Query("""
			select p.boardGame.id as gameId, count(p) as playCount, max(p.playedOn) as lastPlayedOn
			from GamePlay p
			where p.boardGame.id = :gameId and p.boardGame.owner.id = :ownerId
			group by p.boardGame.id
			""")
	Optional<PlayStats> findPlayStatsByGame(@Param("gameId") UUID gameId, @Param("ownerId") UUID ownerId);
}
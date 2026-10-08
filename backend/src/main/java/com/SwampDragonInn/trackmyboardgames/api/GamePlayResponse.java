package com.SwampDragonInn.trackmyboardgames.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.SwampDragonInn.trackmyboardgames.domain.GamePlay;

// The play data returned to the frontend, including the id of the game it belongs to.
public record GamePlayResponse(
		UUID id,
		UUID gameId,
		LocalDate playedOn,
		Integer durationMinutes,
		Integer playerCount,
		String notes,
		Instant createdAt,
		Instant updatedAt)
{

	public static GamePlayResponse from(GamePlay play)
	{
		return new GamePlayResponse(
				play.getId(),
				play.getBoardGame().getId(),
				play.getPlayedOn(),
				play.getDurationMinutes(),
				play.getPlayerCount(),
				play.getNotes(),
				play.getCreatedAt(),
				play.getUpdatedAt());
	}
}

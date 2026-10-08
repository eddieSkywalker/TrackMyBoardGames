package com.SwampDragonInn.trackmyboardgames.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;

// Describes the game data sent back to the frontend, including a summary of its play history.
public record BoardGameResponse(
		UUID id,
		String name,
		Integer minPlayers,
		Integer maxPlayers,
		String bestPlayers,
		Integer playTimeMinutes,
		String notes,
		long playCount,
		LocalDate lastPlayedOn,
		Instant createdAt,
		Instant updatedAt)
{

	public static BoardGameResponse from(BoardGame game, long playCount, LocalDate lastPlayedOn)
	{
		return new BoardGameResponse(
				game.getId(),
				game.getName(),
				game.getMinPlayers(),
				game.getMaxPlayers(),
				game.getBestPlayers(),
				game.getPlayTimeMinutes(),
				game.getNotes(),
				playCount,
				lastPlayedOn,
				game.getCreatedAt(),
				game.getUpdatedAt());
	}
}
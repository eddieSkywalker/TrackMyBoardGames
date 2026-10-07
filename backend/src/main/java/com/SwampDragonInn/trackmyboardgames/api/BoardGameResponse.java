package com.SwampDragonInn.trackmyboardgames.api;

import java.time.Instant;
import java.util.UUID;

import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;

// Describes the game data sent back to the frontend.
public record BoardGameResponse(
		UUID id,
		String name,
		Integer minPlayers,
		Integer maxPlayers,
		Integer bestPlayers,
		Integer playTimeMinutes,
		String notes,
		Instant createdAt,
		Instant updatedAt)
{

	public static BoardGameResponse from(BoardGame game)
	{
		return new BoardGameResponse(
				game.getId(),
				game.getName(),
				game.getMinPlayers(),
				game.getMaxPlayers(),
				game.getBestPlayers(),
				game.getPlayTimeMinutes(),
				game.getNotes(),
				game.getCreatedAt(),
				game.getUpdatedAt());
	}
}
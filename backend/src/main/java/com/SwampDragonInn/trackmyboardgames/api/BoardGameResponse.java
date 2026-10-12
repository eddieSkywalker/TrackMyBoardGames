package com.SwampDragonInn.trackmyboardgames.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import com.SwampDragonInn.trackmyboardgames.domain.BoardGame;

// Describes the game data sent back to the frontend, including a summary of its play history.
// coverImage is the cover photo written as text ("data:image/jpeg;base64,..."), or null when there is none.
public record BoardGameResponse(
		UUID id,
		String name,
		String edition,
		Integer minPlayers,
		Integer maxPlayers,
		String bestPlayers,
		Integer playTimeMinutes,
		String notes,
		long playCount,
		LocalDate lastPlayedOn,
		String coverImage,
		Instant createdAt,
		Instant updatedAt)
{

	public static BoardGameResponse from(BoardGame game, long playCount, LocalDate lastPlayedOn)
	{
		return new BoardGameResponse(
				game.getId(),
				game.getName(),
				game.getEdition().isEmpty() ? null : game.getEdition(),
				game.getMinPlayers(),
				game.getMaxPlayers(),
				game.getBestPlayers(),
				game.getPlayTimeMinutes(),
				game.getNotes(),
				playCount,
				lastPlayedOn,
				toDataUrl(game),
				game.getCreatedAt(),
				game.getUpdatedAt());
	}

	// Turn the stored picture bytes into text that the browser can show directly as an image source.
	private static String toDataUrl(BoardGame game)
	{
		if (!game.hasCover())
		{
			return null;
		}

		String base64 = Base64.getEncoder().encodeToString(game.getCoverImage());
		return "data:" + game.getCoverContentType() + ";base64," + base64;
	}}

package com.SwampDragonInn.trackmyboardgames.event;

import java.time.LocalDate;
import java.util.UUID;

// Version 1 payload shared by play events; excludes private notes and authentication tokens.
public record PlaySnapshot(
		UUID playId,
		UUID gameId,
		LocalDate playedOn,
		Integer durationMinutes,
		Integer playerCount)
{
}

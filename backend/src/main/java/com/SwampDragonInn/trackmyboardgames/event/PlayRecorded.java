package com.SwampDragonInn.trackmyboardgames.event;

import java.time.LocalDate;
import java.util.UUID;

// Version 1 payload: a snapshot for statistics used to help populate the Stats component on the home page
public record PlayRecorded(
		UUID playId,
		UUID gameId,
		LocalDate playedOn,
		Integer durationMinutes,
		Integer playerCount)
{
}

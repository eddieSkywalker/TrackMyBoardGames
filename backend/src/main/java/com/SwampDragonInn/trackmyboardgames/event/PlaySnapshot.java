package com.SwampDragonInn.trackmyboardgames.event;

import java.time.LocalDate;
import java.util.UUID;

// A copy of a recorded play's details at the time it was added, edited, or deleted.
// The play service saves these details in an outbox event, which is later sent to RabbitMQ.
// The play and game IDs say which records changed; playedOn says when the game was played,
// not when the user entered it. Duration and player count can be null if the user left them blank.
// This is the version 1 message format, not the play's database record. Private notes and login
// information are not included in the message.
public record PlaySnapshot(
		UUID playId,
		UUID gameId,
		LocalDate playedOn,
		Integer durationMinutes,
		Integer playerCount)
{
}

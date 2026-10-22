package com.SwampDragonInn.trackmyboardgames.event;

import java.util.UUID;

// A copy of a game's ID, name, and edition at the time it was added, edited, or deleted.
// The game service saves these details in an outbox event, which is later sent to RabbitMQ.
// Keeping a copy means the message still describes the game even after its database record is deleted.
// This is the version 1 message format; it is not the game's database record or its statistics summary.
public record GameSnapshot(
		UUID gameId,
		String name,
		String edition)
{
}

package com.SwampDragonInn.trackmyboardgames.event;

import java.util.UUID;

// Version 1 payload shared by game events; includes fields that identify a game in statistics.
public record GameSnapshot(
		UUID gameId,
		String name,
		String edition)
{
}

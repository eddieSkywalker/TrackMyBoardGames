package com.SwampDragonInn.trackmyboardgames.api;

import java.util.UUID;

/**
 * Describes a collection in the selector without sending its background picture or games.
 */
public record CollectionSummaryResponse(UUID id, String name, boolean defaultCollection)
{
}

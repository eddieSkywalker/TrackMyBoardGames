package com.SwampDragonInn.trackmyboardgames.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Accepts a collection name; empty names and names longer than the database allows are rejected.
 */
public record CollectionNameRequest(@NotBlank @Size(max = 200) String name)
{
}

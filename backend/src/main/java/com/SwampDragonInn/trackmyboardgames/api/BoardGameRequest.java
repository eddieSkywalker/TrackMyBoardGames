package com.SwampDragonInn.trackmyboardgames.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Describes game data accepted from the frontend; validation annotations reject invalid input.
public record BoardGameRequest(
		@NotBlank @Size(max = 200) String name,
		@Min(1) Integer minPlayers,
		@Min(1) Integer maxPlayers,
		@Positive Integer playTimeMinutes,
		@Size(max = 4000) String notes)
{
}
package com.SwampDragonInn.trackmyboardgames.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Describes game data accepted from the frontend; validation annotations reject invalid input.
// edition is optional (for example "2020 edition") and tells apart two copies of a game with the same name.
// coverImage is the cover photo written as text ("data:image/jpeg;base64,..."); null means the game has no cover.
public record BoardGameRequest(
		@NotBlank @Size(max = 200) String name,
		@Size(max = 100) String edition,
		@Min(1) Integer minPlayers,
		@Min(1) Integer maxPlayers,
		@Size(max = 20) String bestPlayers,
		@Positive Integer playTimeMinutes,
		@Size(max = 4000) String notes,
		@Size(max = 7_000_000) String coverImage)
{
}
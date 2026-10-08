package com.SwampDragonInn.trackmyboardgames.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Describes play data accepted from the frontend; only the played date is required.
public record GamePlayRequest(
		@NotNull LocalDate playedOn,
		@Min(1) Integer durationMinutes,
		@Min(1) Integer playerCount,
		@Size(max = 4000) String notes)
{
}

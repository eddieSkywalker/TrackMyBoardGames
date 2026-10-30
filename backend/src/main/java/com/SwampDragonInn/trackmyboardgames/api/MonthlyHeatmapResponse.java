package com.SwampDragonInn.trackmyboardgames.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Supplies every day of the current UTC month and its saved play count.
 * Days are empty when statistics have not been calculated yet, rather than pretending there were no plays.
 */
public record MonthlyHeatmapResponse(String month, String timezone, List<Day> days, Instant refreshedAt)
{

	public record Day(LocalDate date, long playCount)
	{
	}
}

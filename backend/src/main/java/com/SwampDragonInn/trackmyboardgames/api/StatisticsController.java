package com.SwampDragonInn.trackmyboardgames.api;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SwampDragonInn.trackmyboardgames.service.StatisticsReadService;

// Returns the signed-in user's statistics for the Statistics component on the collection page:
// collection size, most-played game, plays this month, and most recently played game.
// The user must be signed in and have permission to read their games before this code runs.
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController
{

	private final StatisticsReadService statistics;

	public StatisticsController(StatisticsReadService statistics)
	{
		this.statistics = statistics;
	}

	/**
	 * Reads the current month's saved daily counts for all collections or one chosen shelf.
	 */
	@GetMapping("/heatmap")
	public ResponseEntity<MonthlyHeatmapResponse> heatmap(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) UUID collectionId)
	{
		String issuer = jwt.getIssuer().toString();
		String subject = jwt.getSubject();
		MonthlyHeatmapResponse savedHeatmap = this.statistics.readMonthlyHeatmap(issuer, subject, collectionId);

		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(savedHeatmap);
	}

	// Handles the collection page's request for saved statistics.
	@GetMapping
	public ResponseEntity<CollectionStatisticsResponse> get(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) UUID collectionId)
	{
		// The validated token identifies who issued the login and which user signed in.
		String issuer = jwt.getIssuer().toString();
		String subject = jwt.getSubject();

		// Look up the statistics already saved for this user instead of calculating them again.
		CollectionStatisticsResponse savedStatistics = this.statistics.readCollectionStats(issuer, subject, collectionId);

		// Send the statistics back to the page. Tell browsers and other caches not to save a copy,
		// so future requests fetch the saved results again rather than reuse an old response.
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(savedStatistics);
	}
}

package com.SwampDragonInn.trackmyboardgames.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// The saved answers for one user's four statistics cards. Events refresh these answers in the background,
// so a future statistics request will not need to count the user's full play history again.
@Entity
@Table(name = "statistics_summaries")
public class StatisticsSummary
{

	// Identifies a winning game without loading its cover image or private notes.
	public record GameHighlight(UUID gameId, String name, String edition, long playCount, LocalDate lastPlayedOn)
	{
	}

	@Id
	@Column(name = "owner_id", updatable = false)
	private UUID ownerId;

	@Column(name = "collection_size", nullable = false)
	private long collectionSize;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "most_played", columnDefinition = "jsonb")
	private GameHighlight mostPlayed;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "most_recently_played", columnDefinition = "jsonb")
	private GameHighlight mostRecentlyPlayed;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "monthly_plays", nullable = false, columnDefinition = "jsonb")
	private Map<String, Long> monthlyPlays;

	@Column(name = "refreshed_at", nullable = false)
	private Instant refreshedAt;

	// JPA uses this constructor when reading the saved cards.
	protected StatisticsSummary()
	{
	}

	// Builds the replacement answers after counting the owner's current games and plays.
	public StatisticsSummary(UUID ownerId, long collectionSize, GameHighlight mostPlayed,
			GameHighlight mostRecentlyPlayed, Map<String, Long> monthlyPlays, Instant refreshedAt)
	{
		this.ownerId = Objects.requireNonNull(ownerId, "Owner ID is required.");
		this.collectionSize = collectionSize;
		this.mostPlayed = mostPlayed;
		this.mostRecentlyPlayed = mostRecentlyPlayed;
		this.monthlyPlays = Map.copyOf(monthlyPlays);
		this.refreshedAt = Objects.requireNonNull(refreshedAt, "Refresh time is required.");
	}

	// Returns whose statistics these are; the future API must use the signed-in user's ID.
	public UUID getOwnerId()
	{
		return this.ownerId;
	}

	// Returns the number of games currently in the owner's collection.
	public long getCollectionSize()
	{
		return this.collectionSize;
	}

	// Returns the game with the most plays, or null when no games have been played.
	public GameHighlight getMostPlayed()
	{
		return this.mostPlayed;
	}

	// Returns the game with the latest played-on date, not the latest date a play was entered.
	public GameHighlight getMostRecentlyPlayed()
	{
		return this.mostRecentlyPlayed;
	}

	// Looks up a month without another recount; a month with no recorded plays has a count of zero.
	// Keeping each month separately lets the current-month card change when the calendar rolls over.
	public long getPlaysForMonth(YearMonth month)
	{
		return this.monthlyPlays.getOrDefault(Objects.requireNonNull(month, "Month is required.").toString(), 0L);
	}

	// Returns when a background refresh last saved these answers.
	public Instant getRefreshedAt()
	{
		return this.refreshedAt;
	}
}

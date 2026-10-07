package com.SwampDragonInn.trackmyboardgames.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

// JPA model for one time a game was played; each row belongs to exactly one board game.
@Entity
@Table(name = "game_plays")
public class GamePlay
{

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "board_game_id", nullable = false)
	private BoardGame boardGame;

	@Column(name = "played_on", nullable = false)
	private LocalDate playedOn;

	@Column(name = "duration_minutes")
	private Integer durationMinutes;

	@Column(name = "player_count")
	private Integer playerCount;

	@Column(length = 4000)
	private String notes;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected GamePlay()
	{
	}

	public GamePlay(BoardGame boardGame, LocalDate playedOn, Integer durationMinutes,
			Integer playerCount, String notes)
	{
		this.boardGame = boardGame;
		updateDetails(playedOn, durationMinutes, playerCount, notes);
	}

	public void updateDetails(LocalDate playedOn, Integer durationMinutes, Integer playerCount, String notes)
	{
		this.playedOn = playedOn;
		this.durationMinutes = durationMinutes;
		this.playerCount = playerCount;
		this.notes = notes;
	}

	@PrePersist
	void setCreatedAt()
	{
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void setUpdatedAt()
	{
		this.updatedAt = Instant.now();
	}

	public UUID getId()
	{
		return this.id;
	}

	public BoardGame getBoardGame()
	{
		return this.boardGame;
	}

	public LocalDate getPlayedOn()
	{
		return this.playedOn;
	}

	public Integer getDurationMinutes()
	{
		return this.durationMinutes;
	}

	public Integer getPlayerCount()
	{
		return this.playerCount;
	}

	public String getNotes()
	{
		return this.notes;
	}

	public Instant getCreatedAt()
	{
		return this.createdAt;
	}

	public Instant getUpdatedAt()
	{
		return this.updatedAt;
	}
}

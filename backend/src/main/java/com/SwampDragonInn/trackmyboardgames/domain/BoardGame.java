package com.SwampDragonInn.trackmyboardgames.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;

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

// JPA model for a board game; each row belongs to one local app user through owner.
// DynamicUpdate makes saves write only the columns that changed, so editing a note does not resend the cover picture.
@DynamicUpdate
@Entity
@Table(name = "board_games")
public class BoardGame
{

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private AppUser owner;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(name = "min_players")
	private Integer minPlayers;

	@Column(name = "max_players")
	private Integer maxPlayers;

	@Column(name = "best_players", length = 20)
	private String bestPlayers;

	@Column(name = "play_time_minutes")
	private Integer playTimeMinutes;

	@Column(length = 4000)
	private String notes;

	// The cover photo: empty (null) when the game has none. The content type says if the bytes are JPEG, PNG or WebP.
	@Column(name = "cover_content_type", length = 50)
	private String coverContentType;

	@Column(name = "cover_image")
	private byte[] coverImage;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected BoardGame()
	{
	}

	public BoardGame(AppUser owner, String name, Integer minPlayers, Integer maxPlayers,
			String bestPlayers, Integer playTimeMinutes, String notes)
	{
		this.owner = owner;
		updateDetails(name, minPlayers, maxPlayers, bestPlayers, playTimeMinutes, notes);
	}

	public void updateDetails(String name, Integer minPlayers, Integer maxPlayers,
			String bestPlayers, Integer playTimeMinutes, String notes)
	{
		this.name = name;
		this.minPlayers = minPlayers;
		this.maxPlayers = maxPlayers;
		this.bestPlayers = bestPlayers;
		this.playTimeMinutes = playTimeMinutes;
		this.notes = notes;
	}

	// Save a new cover photo, replacing any existing one.
	public void setCover(String contentType, byte[] image)
	{
		this.coverContentType = contentType;
		this.coverImage = image;
	}

	public void removeCover()
	{
		this.coverContentType = null;
		this.coverImage = null;
	}

	public boolean hasCover()
	{
		return this.coverImage != null;
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

	public AppUser getOwner()
	{
		return this.owner;
	}

	public String getName()
	{
		return this.name;
	}

	public Integer getMinPlayers()
	{
		return this.minPlayers;
	}

	public Integer getMaxPlayers()
	{
		return this.maxPlayers;
	}

	public String getBestPlayers()
	{
		return this.bestPlayers;
	}

	public Integer getPlayTimeMinutes()
	{
		return this.playTimeMinutes;
	}

	public String getNotes()
	{
		return this.notes;
	}

	public String getCoverContentType()
	{
		return this.coverContentType;
	}

	public byte[] getCoverImage()
	{
		return this.coverImage;
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
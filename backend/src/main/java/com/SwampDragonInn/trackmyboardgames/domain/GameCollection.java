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
import jakarta.persistence.Table;

/**
 * Stores one user's named collection and background.
 * Only changed fields are saved, so renaming does not overwrite a background saved by another request.
 */
@DynamicUpdate
@Entity
@Table(name = "game_collections")
public class GameCollection
{

	public static final String DEFAULT_NAME = "My collection";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private AppUser owner;

	@Column(nullable = false, length = 200)
	private String name;

	// The picture shown behind the "Game shelf" heading: empty (null) when none has been set.
	@Column(name = "background_content_type", length = 50)
	private String backgroundContentType;

	@Column(name = "background_image")
	private byte[] backgroundImage;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected GameCollection()
	{
	}

	/**
	 * Creates a named collection belonging to the supplied owner.
	 */
	public GameCollection(AppUser owner, String name)
	{
		this.owner = owner;
		this.name = name;
	}

	/**
	 * Changes the name while leaving the collection's games and background untouched.
	 */
	public void rename(String name)
	{
		this.name = name;
	}

	/**
	 * Saves a background picture, replacing any existing one.
	 */
	public void setBackground(String contentType, byte[] image)
	{
		this.backgroundContentType = contentType;
		this.backgroundImage = image;
	}

	/**
	 * Removes the background picture and its image type together.
	 */
	public void removeBackground()
	{
		this.backgroundContentType = null;
		this.backgroundImage = null;
	}

	@PrePersist
	void setCreatedAt()
	{
		this.createdAt = Instant.now();
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

	public String getBackgroundContentType()
	{
		return this.backgroundContentType;
	}

	public byte[] getBackgroundImage()
	{
		return this.backgroundImage;
	}
}

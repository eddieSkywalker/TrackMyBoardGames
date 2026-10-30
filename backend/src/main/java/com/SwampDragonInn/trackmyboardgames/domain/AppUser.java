package com.SwampDragonInn.trackmyboardgames.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// JPA model for one local app profile, linked to Auth0 by its issuer and stable subject ID.
@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(
		name = "uq_app_users_auth0_identity", columnNames = { "auth0_issuer", "auth0_subject" }))
public class AppUser
{

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "auth0_issuer", nullable = false, length = 255)
	private String auth0Issuer;

	@Column(name = "auth0_subject", nullable = false, length = 255)
	private String auth0Subject;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "default_collection_id")
	private GameCollection defaultCollection;

	/**
	 * Returns the saved starting/default collection, or null during initial profile setup.
	 */
	public GameCollection getDefaultCollection()
	{
		return this.defaultCollection;
	}

	/**
	 * Sets the default only to a collection owned by this profile.
	 *
	 * @param collection an existing collection belonging to this user
	 */
	public void setDefaultCollection(GameCollection collection)
	{
		if (collection == null || !this.id.equals(collection.getOwner().getId()))
		{
			throw new IllegalArgumentException("The default collection must belong to this user.");
		}
		this.defaultCollection = collection;
	}

	protected AppUser()
	{
	}

	public AppUser(String auth0Issuer, String auth0Subject)
	{
		this.auth0Issuer = auth0Issuer;
		this.auth0Subject = auth0Subject;
	}

	@PrePersist
	void setCreatedAt()
	{
		if (this.createdAt == null)
		{
			this.createdAt = Instant.now();
		}
	}

	public UUID getId()
	{
		return this.id;
	}

	public String getAuth0Issuer()
	{
		return this.auth0Issuer;
	}

	public String getAuth0Subject()
	{
		return this.auth0Subject;
	}

	public Instant getCreatedAt()
	{
		return this.createdAt;
	}
}
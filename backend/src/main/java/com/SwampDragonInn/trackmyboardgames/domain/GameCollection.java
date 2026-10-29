package com.SwampDragonInn.trackmyboardgames.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	@Enumerated(EnumType.STRING)
	@Column(name = "plan_tier", nullable = false, length = 20)
	private CollectionTier planTier = CollectionTier.FREE;

	@Enumerated(EnumType.STRING)
	@Column(name = "permanent_tier", nullable = false, length = 20)
	private CollectionTier permanentTier = CollectionTier.FREE;

	@Enumerated(EnumType.STRING)
	@Column(name = "subscription_status", nullable = false, length = 20)
	private CollectionSubscriptionStatus subscriptionStatus = CollectionSubscriptionStatus.INACTIVE;

	@Column(name = "subscription_ends_at")
	private Instant subscriptionEndsAt;

	// Lifetime confirmed payments in USD cents; subscriber months count successfully paid monthly periods.
	@Column(name = "total_payments", nullable = false)
	private long totalPayments;

	@Column(name = "subscriber_months", nullable = false)
	private long subscriberMonths;

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

	public void setPlanTier(CollectionTier planTier)
	{
		this.planTier = planTier;
	}

	public void setPermanentTier(CollectionTier permanentTier)
	{
		this.permanentTier = permanentTier;
	}

	public void setSubscriptionStatus(CollectionSubscriptionStatus subscriptionStatus)
	{
		this.subscriptionStatus = subscriptionStatus;
	}

	public void setSubscriptionEndsAt(Instant subscriptionEndsAt)
	{
		this.subscriptionEndsAt = subscriptionEndsAt;
	}

	public void setTotalPayments(long totalPayments)
	{
		this.totalPayments = totalPayments;
	}

	public void setSubscriberMonths(long subscriberMonths)
	{
		this.subscriberMonths = subscriberMonths;
	}

	public CollectionTier getPlanTier()
	{
		return this.planTier;
	}

	public CollectionTier getPermanentTier()
	{
		return this.permanentTier;
	}

	public CollectionSubscriptionStatus getSubscriptionStatus()
	{
		return this.subscriptionStatus;
	}

	public Instant getSubscriptionEndsAt()
	{
		return this.subscriptionEndsAt;
	}

	public long getTotalPayments()
	{
		return this.totalPayments;
	}

	public long getSubscriberMonths()
	{
		return this.subscriberMonths;
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

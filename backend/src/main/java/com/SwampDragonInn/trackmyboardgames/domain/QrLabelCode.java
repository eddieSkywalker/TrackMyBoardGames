package com.SwampDragonInn.trackmyboardgames.domain;

import java.time.Instant;
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
import jakarta.persistence.Table;

// JPA model for one printed QR label: a random token that points at a game and can be revoked.
@Entity
@Table(name = "qr_label_codes")
public class QrLabelCode
{

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private AppUser owner;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "board_game_id", nullable = false)
	private BoardGame boardGame;

	@Column(nullable = false, unique = true, updatable = false, length = 64)
	private String token;

	// Empty (null) while the label is active; set to the revoke time once it stops working.
	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected QrLabelCode()
	{
	}

	public QrLabelCode(AppUser owner, BoardGame boardGame, String token)
	{
		this.owner = owner;
		this.boardGame = boardGame;
		this.token = token;
	}

	public void revoke()
	{
		if (this.revokedAt == null)
		{
			this.revokedAt = Instant.now();
		}
	}

	public boolean isRevoked()
	{
		return this.revokedAt != null;
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

	public BoardGame getBoardGame()
	{
		return this.boardGame;
	}

	public String getToken()
	{
		return this.token;
	}

	public Instant getRevokedAt()
	{
		return this.revokedAt;
	}

	public Instant getCreatedAt()
	{
		return this.createdAt;
	}
}
package com.SwampDragonInn.trackmyboardgames.api;

import java.time.Instant;
import java.util.UUID;

import com.SwampDragonInn.trackmyboardgames.domain.QrLabelCode;

// The label data returned to the frontend; the token is what the QR code should contain.
public record QrLabelResponse(
		UUID id,
		UUID gameId,
		String token,
		boolean revoked,
		Instant createdAt,
		Instant revokedAt)
{

	public static QrLabelResponse from(QrLabelCode label)
	{
		return new QrLabelResponse(
				label.getId(),
				label.getBoardGame().getId(),
				label.getToken(),
				label.isRevoked(),
				label.getCreatedAt(),
				label.getRevokedAt());
	}
}
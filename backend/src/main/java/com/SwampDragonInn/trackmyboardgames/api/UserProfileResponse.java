package com.SwampDragonInn.trackmyboardgames.api;

import java.time.Instant;
import java.util.UUID;

import com.SwampDragonInn.trackmyboardgames.domain.AppUser;

// Describes the local profile fields returned by the current-user API endpoint.
public record UserProfileResponse(UUID id, String subject, Instant createdAt)
{

	public static UserProfileResponse from(AppUser user)
	{
		return new UserProfileResponse(user.getId(), user.getAuth0Subject(), user.getCreatedAt());
	}
}
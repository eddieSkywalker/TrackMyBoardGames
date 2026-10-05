package com.SwampDragonInn.trackmyboardgames.api;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.SwampDragonInn.trackmyboardgames.service.UserProfileService;

// Provides general API endpoints: a public health check and the signed-in user's local profile.
@RestController
@RequestMapping("/api")
public class ApiController
{

	private final UserProfileService userProfileService;

	public ApiController(UserProfileService userProfileService)
	{
		this.userProfileService = userProfileService;
	}

	@GetMapping("/health")
	public Map<String, String> health()
	{
		return Map.of("status", "ok");
	}

	@GetMapping("/me")
	public UserProfileResponse currentUser(@AuthenticationPrincipal Jwt jwt)
	{
		return UserProfileResponse.from(this.userProfileService.findOrCreate(jwt));
	}
}
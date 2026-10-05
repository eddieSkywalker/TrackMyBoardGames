package com.SwampDragonInn.trackmyboardgames.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.repository.AppUserRepository;

// Converts a validated Auth0 identity into its matching local profile, creating one on first use.
@Service
public class UserProfileService
{

	private final AppUserRepository appUserRepository;

	public UserProfileService(AppUserRepository appUserRepository)
	{
		this.appUserRepository = appUserRepository;
	}

	// Find or create the local profile using the issuer and subject from a validated Auth0 token.
	@Transactional
	public AppUser findOrCreate(Jwt jwt)
	{
		return findOrCreate(jwt.getIssuer().toString(), jwt.getSubject());
	}

	// Reuse the matching local profile, or save one the first time this Auth0 identity is seen.
	@Transactional
	public AppUser findOrCreate(String issuer, String subject)
	{
		return this.appUserRepository.findByAuth0IssuerAndAuth0Subject(issuer, subject)
				.orElseGet(() -> this.appUserRepository.save(new AppUser(issuer, subject)));
	}
}
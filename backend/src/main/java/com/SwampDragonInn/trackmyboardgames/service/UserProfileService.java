package com.SwampDragonInn.trackmyboardgames.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.repository.AppUserRepository;
import com.SwampDragonInn.trackmyboardgames.repository.GameCollectionRepository;
import com.SwampDragonInn.trackmyboardgames.domain.GameCollection;
import jakarta.persistence.EntityManager;

/**
 * Matches a validated Auth0 login to a local profile and ensures it has a starting collection.
 */
@Service
public class UserProfileService
{

	private final AppUserRepository appUserRepository;
	private final GameCollectionRepository collections;
	private final EntityManager entityManager;

	/**
	 * Supplies profile and collection access, plus the helper used to reload a profile after waiting for setup.
	 */
	public UserProfileService(AppUserRepository appUserRepository, GameCollectionRepository collections, EntityManager entityManager)
	{
		this.appUserRepository = appUserRepository;
		this.collections = collections;
		this.entityManager = entityManager;
	}

	/**
	 * Reads the login provider and user identity from a validated token, then delegates to profile setup.
	 */
	@Transactional
	public AppUser findOrCreate(Jwt jwt)
	{
		return findOrCreate(jwt.getIssuer().toString(), jwt.getSubject());
	}

	/**
	 * Finds or creates a profile and its starting collection, saving both together or neither on failure.
	 *
	 * <p>If first requests arrive together, they take turns setting up the same profile.
	 * The later request reuses the finished setup instead of creating another collection.
	 * An existing shelf collection is reused when the profile has no saved default yet.
	 *
	 * @param issuer validated login provider
	 * @param subject validated user identity within that provider
	 * @return profile with its default collection initialized
	 */
	@Transactional
	public AppUser findOrCreate(String issuer, String subject)
	{
		AppUser existing = this.appUserRepository.findByAuth0IssuerAndAuth0Subject(issuer, subject).orElse(null);
		if (existing != null && existing.getDefaultCollection() != null)
		{
			return existing;
		}

		this.appUserRepository.insertIfMissing(issuer, subject);
		AppUser owner = this.appUserRepository.findForCollectionSetup(issuer, subject).orElseThrow();

		// Reload after waiting for the lock: another request may have finished setup in the meantime.
		this.entityManager.refresh(owner);

		if (owner.getDefaultCollection() == null)
		{
			GameCollection collection = this.collections.findFirstByOwner_IdOrderByCreatedAtAscIdAsc(owner.getId())
					.orElseGet(() -> this.collections.saveAndFlush(new GameCollection(owner, GameCollection.DEFAULT_NAME)));
			owner.setDefaultCollection(collection);
			this.appUserRepository.flush();
		}

		return owner;
	}
}
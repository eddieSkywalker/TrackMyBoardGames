package com.SwampDragonInn.trackmyboardgames.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import com.SwampDragonInn.trackmyboardgames.domain.AppUser;

import jakarta.persistence.LockModeType;

// Database access for profiles, including lookup by the user's Auth0 identity.
public interface AppUserRepository extends JpaRepository<AppUser, UUID>
{

	// Looks up the local profile for an Auth0 identity. Optional means the result either contains
	// an AppUser or is empty when no matching profile exists; the caller decides what to do next.
	Optional<AppUser> findByAuth0IssuerAndAuth0Subject(String auth0Issuer, String auth0Subject);

	/**
	 * Creates a local profile for this login identity only if one does not already exist.
	 * If two requests try at once, one creates it and the other leaves it alone instead of failing.
	 * The database supplies the new profile's ID and creation time.
	 *
	 * <p>{@code @Modifying} tells Spring this query changes data rather than just reading it.
	 */
	@Modifying
	@Query(value = """
			insert into app_users (id, auth0_issuer, auth0_subject, created_at)
			values (gen_random_uuid(), :issuer, :subject, current_timestamp)
			on conflict (auth0_issuer, auth0_subject) do nothing
			""", nativeQuery = true)
	void insertIfMissing(@Param("issuer") String issuer, @Param("subject") String subject);

	/**
	 * Finds the profile and reserves its row until the service's transaction finishes.
	 * Another request using this lock for the same user waits, then reuses the completed setup.
	 * This prevents overlapping first requests from creating two starting collections.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select owner from AppUser owner where owner.auth0Issuer = :issuer and owner.auth0Subject = :subject")
	Optional<AppUser> findForCollectionSetup(@Param("issuer") String issuer, @Param("subject") String subject);

	// Finds the owner before recalculating all four saved statistics together in the background.
	// This is not a browser refresh, and it does not update the user's profile.
	// @Lock asks PostgreSQL to reserve this user's row until the caller's transaction finishes.
	// PESSIMISTIC_WRITE makes another worker requesting this lock for the same user wait its turn,
	// so two recalculations cannot overwrite each other's results. Other users have separate rows.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	// @Query selects the user whose ID matches ownerId; @Param connects the argument to :ownerId.
	@Query("select owner from AppUser owner where owner.id = :ownerId")
	// Optional contains the owner if found, or is empty if the user no longer exists.
	// The statistics service currently treats an empty result as an error.
	Optional<AppUser> findForStatisticsRefresh(@Param("ownerId") UUID ownerId);

	// Reads a small page of IDs, not full profiles. The last ID is a bookmark for the next page,
	// so finishing a user's backfill does not move other users between pages.
	@Query(value = """
			select id from app_users
			where cast(:afterId as uuid) is null or id > cast(:afterId as uuid)
			order by id
			""", nativeQuery = true)
	List<UUID> findIdsForStatisticsBackfill(@Param("afterId") UUID afterId, Pageable pageable);
}
package com.SwampDragonInn.trackmyboardgames.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.SwampDragonInn.trackmyboardgames.domain.AppUser;

import jakarta.persistence.LockModeType;

// Database access for profiles, including lookup by the user's Auth0 identity.
public interface AppUserRepository extends JpaRepository<AppUser, UUID>
{

	// Looks up the local profile for an Auth0 identity. Optional means the result either contains
	// an AppUser or is empty when no matching profile exists; the caller decides what to do next.
	Optional<AppUser> findByAuth0IssuerAndAuth0Subject(String auth0Issuer, String auth0Subject);

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
}
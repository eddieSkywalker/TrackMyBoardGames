package com.SwampDragonInn.trackmyboardgames.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SwampDragonInn.trackmyboardgames.domain.AppUser;

// Database access for profiles, including lookup by the user's Auth0 identity.
public interface AppUserRepository extends JpaRepository<AppUser, UUID>
{

	Optional<AppUser> findByAuth0IssuerAndAuth0Subject(String auth0Issuer, String auth0Subject);
}
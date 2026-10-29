package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.CollectionStatisticsSummary;
import com.SwampDragonInn.trackmyboardgames.domain.CollectionStatisticsSummary.GameSummary;
import com.SwampDragonInn.trackmyboardgames.repository.AppUserRepository;

import jakarta.persistence.EntityManager;

// Exercises authentication, owner isolation, and the actual JSON returned for saved or pending statistics.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StatisticsApiTests
{

	private static final String ISSUER = "https://example.test/";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private AppUserRepository users;

	@Test
	void requiresAuthenticationAndReadGamesPermission() throws Exception
	{
		this.mvc.perform(get("/api/statistics")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/statistics").with(jwt()))
				.andExpect(status().isForbidden());
		this.mvc.perform(get("/api/statistics").with(jwt()
				.authorities(new SimpleGrantedAuthority("SCOPE_write:games"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void returnsAllFourSavedCardsAndFreshnessWithoutRecalculating() throws Exception
	{
		String subject = "statistics-" + UUID.randomUUID();
		AppUser owner = owner(ISSUER, subject);
		YearMonth month = YearMonth.now(ZoneOffset.UTC);
		UUID mostPlayedId = UUID.randomUUID();
		UUID mostRecentId = UUID.randomUUID();
		Instant refreshedAt = Instant.parse("2026-01-01T10:00:00Z");
		this.entityManager.persist(new CollectionStatisticsSummary(owner.getId(), 12,
				new GameSummary(mostPlayedId, "Azul", "", 8, LocalDate.of(2026, 1, 1)),
				new GameSummary(mostRecentId, "Wingspan", "Asia", 2, LocalDate.of(2026, 1, 2)),
				Map.of(month.toString(), 5L, month.minusMonths(1).toString(), 99L), refreshedAt));
		this.entityManager.flush();
		this.entityManager.clear();

		// No source games/plays exist in this test: results must come from the saved summary.
		this.mvc.perform(get("/api/statistics").with(token(ISSUER, subject)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.ready").doesNotExist())
				.andExpect(jsonPath("$.collectionSize").value(12))
				.andExpect(jsonPath("$.mostPlayed.gameId").value(mostPlayedId.toString()))
				.andExpect(jsonPath("$.mostPlayed.name").value("Azul"))
				.andExpect(jsonPath("$.mostPlayed.playCount").value(8))
				.andExpect(jsonPath("$.playsThisMonth").value(5))
				.andExpect(jsonPath("$.mostRecentlyPlayed.gameId").value(mostRecentId.toString()))
				.andExpect(jsonPath("$.mostRecentlyPlayed.edition").value("Asia"))
				.andExpect(jsonPath("$.mostRecentlyPlayed.lastPlayedOn").value("2026-01-02"))
				.andExpect(jsonPath("$.month").value(month.toString()))
				.andExpect(jsonPath("$.timezone").value("UTC"))
				.andExpect(jsonPath("$.refreshedAt").value(refreshedAt.toString()))
				.andExpect(jsonPath("$.ownerId").doesNotExist())
				.andExpect(jsonPath("$.monthlyPlays").doesNotExist());
	}

	@Test
	void initializedEmptyCollectionHasActualZeroCounts() throws Exception
	{
		String subject = "empty-" + UUID.randomUUID();
		AppUser owner = owner(ISSUER, subject);
		this.entityManager.persist(new CollectionStatisticsSummary(owner.getId(), 0, null, null, Map.of(), Instant.now()));
		this.entityManager.flush();

		this.mvc.perform(get("/api/statistics").with(token(ISSUER, subject)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ready").doesNotExist())
				.andExpect(jsonPath("$.collectionSize").value(0))
				.andExpect(jsonPath("$.playsThisMonth").value(0))
				.andExpect(jsonPath("$.mostPlayed").value(nullValue()))
				.andExpect(jsonPath("$.mostRecentlyPlayed").value(nullValue()));
	}

	@Test
	void missingSummaryIsPendingRatherThanAnEmptyCollection() throws Exception
	{
		String subject = "pending-" + UUID.randomUUID();
		owner(ISSUER, subject);
		assertPending(ISSUER, subject);
	}

	@Test
	void unknownIdentityIsPendingWithoutCreatingAProfile() throws Exception
	{
		String subject = "new-" + UUID.randomUUID();
		assertPending(ISSUER, subject);
		assertThat(this.users.findByAuth0IssuerAndAuth0Subject(ISSUER, subject)).isEmpty();
	}

	@Test
	void neitherAnotherSubjectNorAnotherIssuerCanReadTheOwnersStatistics() throws Exception
	{
		String subject = "private-" + UUID.randomUUID();
		AppUser owner = owner(ISSUER, subject);
		this.entityManager.persist(new CollectionStatisticsSummary(owner.getId(), 73, null, null, Map.of(), Instant.now()));
		this.entityManager.flush();

		// A caller-supplied owner ID does not change which identity the endpoint looks up.
		this.mvc.perform(get("/api/statistics").param("ownerId", owner.getId().toString())
					.with(token(ISSUER, "other-" + UUID.randomUUID())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ready").doesNotExist())
				.andExpect(jsonPath("$.collectionSize").value(nullValue()));
		assertPending("https://other-issuer.test/", subject);
	}

	private void assertPending(String issuer, String subject) throws Exception
	{
		this.mvc.perform(get("/api/statistics").with(token(issuer, subject)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ready").doesNotExist())
				.andExpect(jsonPath("$.collectionSize").value(nullValue()))
				.andExpect(jsonPath("$.playsThisMonth").value(nullValue()))
				.andExpect(jsonPath("$.mostPlayed").value(nullValue()))
				.andExpect(jsonPath("$.mostRecentlyPlayed").value(nullValue()))
				.andExpect(jsonPath("$.refreshedAt").value(nullValue()));
	}

	private AppUser owner(String issuer, String subject)
	{
		AppUser owner = new AppUser(issuer, subject);
		this.entityManager.persist(owner);
		return owner;
	}

	private JwtRequestPostProcessor token(String issuer, String subject)
	{
		return jwt().jwt(builder -> builder.issuer(issuer).subject(subject))
				.authorities(new SimpleGrantedAuthority("SCOPE_read:games"));
	}
}

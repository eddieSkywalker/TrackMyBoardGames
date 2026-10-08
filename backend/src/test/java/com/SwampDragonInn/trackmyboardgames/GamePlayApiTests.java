package com.SwampDragonInn.trackmyboardgames;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

// Verifies play history CRUD, newest-first ordering, validation, and that plays stay private to the game owner.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GamePlayApiTests
{

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	// Scenario: the owner adds, lists, edits, and deletes plays while a different user is blocked at every step.
	// Also checks plays come back newest first.
	@Test
	void playCrudIsLimitedToTheGameOwner() throws Exception
	{
		// Arrange: an owner, another user, and a game owned by the owner
		String ownerSubject = "auth0|owner-" + UUID.randomUUID();
		String otherSubject = "auth0|other-" + UUID.randomUUID();
		String gameId = createGame(ownerSubject);
		String playsUrl = "/api/games/{gameId}/plays";

		String older = """
				{"playedOn":"2026-01-10","durationMinutes":45,"playerCount":3,"notes":"First game"}
				""";
		String newer = """
				{"playedOn":"2026-02-20"}
				""";

		// Act + Assert: the owner adds a play and the response describes it
		MvcResult created = this.mockMvc.perform(post(playsUrl, gameId)
					.with(token(ownerSubject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(older))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.gameId").value(gameId))
				.andExpect(jsonPath("$.playedOn").value("2026-01-10"))
				.andExpect(jsonPath("$.durationMinutes").value(45))
				.andReturn();
		String location = created.getResponse().getHeader("Location");
		String playId = location.substring(location.lastIndexOf('/') + 1);

		this.mockMvc.perform(post(playsUrl, gameId)
					.with(token(ownerSubject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(newer))
				.andExpect(status().isCreated());

		// Act + Assert: listing returns both plays, newest first
		this.mockMvc.perform(get(playsUrl, gameId).with(token(ownerSubject, "read:games")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].playedOn").value("2026-02-20"))
				.andExpect(jsonPath("$[1].playedOn").value("2026-01-10"));

		// Act + Assert: the other user cannot see, add, edit, or delete the owner's plays (all 404)
		this.mockMvc.perform(get(playsUrl, gameId).with(token(otherSubject, "read:games")))
				.andExpect(status().isNotFound());
		this.mockMvc.perform(post(playsUrl, gameId)
					.with(token(otherSubject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(newer))
				.andExpect(status().isNotFound());

		String updated = """
				{"playedOn":"2026-01-11","durationMinutes":60,"playerCount":4,"notes":"Edited"}
				""";
		this.mockMvc.perform(put(playsUrl + "/{playId}", gameId, playId)
					.with(token(otherSubject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(updated))
				.andExpect(status().isNotFound());
		// Act + Assert: the owner edits the play and the new values come back
		this.mockMvc.perform(put(playsUrl + "/{playId}", gameId, playId)
					.with(token(ownerSubject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(updated))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.playedOn").value("2026-01-11"))
				.andExpect(jsonPath("$.notes").value("Edited"));

		this.mockMvc.perform(delete(playsUrl + "/{playId}", gameId, playId)
					.with(token(otherSubject, "write:games")))
				.andExpect(status().isNotFound());
		// Act + Assert: the owner deletes the play (204 No Content)
		this.mockMvc.perform(delete(playsUrl + "/{playId}", gameId, playId)
					.with(token(ownerSubject, "write:games")))
				.andExpect(status().isNoContent());

		// Assert: only one play is left after the delete
		this.mockMvc.perform(get(playsUrl, gameId).with(token(ownerSubject, "read:games")))
				.andExpect(jsonPath("$.length()").value(1));
	}

	// Scenario: deleting a game also deletes its plays, so asking for them afterward finds nothing.
	@Test
	void playsAreRemovedWhenTheirGameIsDeleted() throws Exception
	{
		// Arrange: a game that has one play
		String ownerSubject = "auth0|owner-" + UUID.randomUUID();
		String gameId = createGame(ownerSubject);

		this.mockMvc.perform(post("/api/games/{gameId}/plays", gameId)
					.with(token(ownerSubject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"playedOn\":\"2026-01-10\"}"))
				.andExpect(status().isCreated());

		// Arrange (cont.): each real request has its own transaction, so drop what this test's single transaction has cached.
		this.entityManager.flush();
		this.entityManager.clear();

		// Act + Assert: deleting the game succeeds
		this.mockMvc.perform(delete("/api/games/{gameId}", gameId).with(token(ownerSubject, "write:games")))
				.andExpect(status().isNoContent());

		// Assert: the game's plays are gone too
		this.mockMvc.perform(get("/api/games/{gameId}/plays", gameId).with(token(ownerSubject, "read:games")))
				.andExpect(status().isNotFound());
	}

	// Scenario: bad data is refused with a 400 and nothing is saved
	// (missing date, zero duration, zero players, or a date in the future).
	@Test
	void playValidationRejectsBadInput() throws Exception
	{
		// Arrange: a game, plus four bad request bodies
		String ownerSubject = "auth0|owner-" + UUID.randomUUID();
		String gameId = createGame(ownerSubject);
		String future = LocalDate.now().plusDays(30).toString();

		// Act + Assert: every bad body is rejected with 400
		for (String body : new String[] {
				"{}",
				"{\"playedOn\":\"2026-01-10\",\"durationMinutes\":0}",
				"{\"playedOn\":\"2026-01-10\",\"playerCount\":0}",
				"{\"playedOn\":\"" + future + "\"}" })
		{
			this.mockMvc.perform(post("/api/games/{gameId}/plays", gameId)
						.with(token(ownerSubject, "write:games"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
					.andExpect(status().isBadRequest());
		}

		this.mockMvc.perform(get("/api/games/{gameId}/plays", gameId).with(token(ownerSubject, "read:games")))
				// Assert: nothing was saved
				.andExpect(content().json("[]"));
	}

	// Scenario: no token gets a 401, and a token without the right permission (scope) gets a 403.
	@Test
	void playEndpointsRequireTheirScopes() throws Exception
	{
		// Act + Assert (no Arrange needed): no token, wrong scope, and read-only scope are each refused
		this.mockMvc.perform(get("/api/games/{gameId}/plays", UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/games/{gameId}/plays", UUID.randomUUID())
					.with(token("auth0|reader", "read:profile")))
				.andExpect(status().isForbidden());
		this.mockMvc.perform(post("/api/games/{gameId}/plays", UUID.randomUUID())
					.with(token("auth0|writer", "read:games"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"playedOn\":\"2026-01-10\"}"))
				.andExpect(status().isForbidden());
	}

	// Helper: creates a game for this user and returns its id, so each test starts with a game to attach plays to.
	private String createGame(String subject) throws Exception
	{
		MvcResult created = this.mockMvc.perform(post("/api/games")
					.with(token(subject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Catan\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		String location = created.getResponse().getHeader("Location");
		return location.substring(location.lastIndexOf('/') + 1);
	}

	// Helper: builds a fake signed-in user (a mock Auth0 token) with the given permissions.
	private JwtRequestPostProcessor token(String subject, String scopes)
	{
		return jwt().jwt(jwt -> jwt
				.issuer("https://dev-i640yykn7qf8bmaw.us.auth0.com/")
				.subject(subject)
				.claim("scope", scopes));
	}
}

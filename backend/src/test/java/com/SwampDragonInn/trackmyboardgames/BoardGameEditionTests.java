package com.SwampDragonInn.trackmyboardgames;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.jayway.jsonpath.JsonPath;

// Verifies the optional edition field: the same game can be added again only with a different edition.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BoardGameEditionTests
{

	@Autowired
	private MockMvc mockMvc;

	// Scenario: Villainous can be added without an edition, but adding it again is refused with advice to use Edition.
	@Test
	void sameNameWithoutEditionIsRefused() throws Exception
	{
		String user = "auth0|user-" + UUID.randomUUID();

		addGame(user, "Villainous", null).andExpect(status().isCreated())
				.andExpect(jsonPath("$.edition").doesNotExist());

		// Upper/lower case and extra spaces do not make it a different game
		addGame(user, "  villainous ", null).andExpect(status().isConflict());
	}

	// Scenario: a different edition is allowed once, then refused when repeated.
	@Test
	void differentEditionsAreAllowed() throws Exception
	{
		String user = "auth0|user-" + UUID.randomUUID();

		addGame(user, "Villainous", null).andExpect(status().isCreated());
		addGame(user, "Villainous", "2020 edition").andExpect(status().isCreated())
				.andExpect(jsonPath("$.edition").value("2020 edition"));
		addGame(user, "Villainous", "2021 edition").andExpect(status().isCreated());
		addGame(user, "Villainous", "2020 EDITION").andExpect(status().isConflict());
	}

	// Scenario: another user can own the same game and edition.
	@Test
	void duplicatesAreCheckedPerUser() throws Exception
	{
		addGame("auth0|one-" + UUID.randomUUID(), "Villainous", null).andExpect(status().isCreated());
		addGame("auth0|two-" + UUID.randomUUID(), "Villainous", null).andExpect(status().isCreated());
	}

	// Scenario: editing a game may keep its own name and edition, but not copy another game's.
	@Test
	void editsAreCheckedAgainstOtherGamesOnly() throws Exception
	{
		String user = "auth0|user-" + UUID.randomUUID();
		addGame(user, "Villainous", null).andExpect(status().isCreated());
		MvcResult second = addGame(user, "Villainous", "2020 edition").andExpect(status().isCreated()).andReturn();
		String secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.id");

		// Saving unchanged details is fine
		updateGame(user, secondId, "Villainous", "2020 edition").andExpect(status().isOk());
		// Removing the edition would make it a copy of the first game
		updateGame(user, secondId, "Villainous", "").andExpect(status().isConflict());
		// A new edition is fine
		updateGame(user, secondId, "Villainous", "Deluxe").andExpect(status().isOk())
				.andExpect(jsonPath("$.edition").value("Deluxe"));
	}

	// Helper: the JSON body for a game, with an optional edition.
	private String json(String name, String edition)
	{
		String editionPart = edition == null ? "" : ",\"edition\":\"" + edition + "\"";
		return "{\"name\":\"" + name + "\"" + editionPart + "}";
	}

	private org.springframework.test.web.servlet.ResultActions addGame(String subject, String name, String edition)
			throws Exception
	{
		return this.mockMvc.perform(post("/api/games")
				.with(token(subject, "write:games"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(name, edition)));
	}

	private org.springframework.test.web.servlet.ResultActions updateGame(String subject, String id, String name,
			String edition) throws Exception
	{
		return this.mockMvc.perform(put("/api/games/{id}", id)
				.with(token(subject, "write:games"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(name, edition)));
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

package com.SwampDragonInn.trackmyboardgames;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import com.SwampDragonInn.trackmyboardgames.repository.AppUserRepository;

// Checks API authentication, scope requirements, profile reuse, and separation between users' games.
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:5173,https://development.dag28nzl054yt.amplifyapp.com")
@AutoConfigureMockMvc
@Transactional
class ApiSecurityTests
{

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository appUserRepository;

	@Test
	void corsPreflightAcceptsConfiguredFrontendOrigins() throws Exception
	{
		for (String origin : new String[] {
				"http://localhost:5173",
				"https://development.dag28nzl054yt.amplifyapp.com" })
		{
			this.mockMvc.perform(options("/api/games")
						.header("Origin", origin)
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers", "Authorization,Content-Type"))
					.andExpect(status().isOk())
					.andExpect(header().string("Access-Control-Allow-Origin", origin))
					.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
		}
	}

	@Test
	void corsPreflightRejectsUnconfiguredOrigins() throws Exception
	{
		this.mockMvc.perform(options("/api/games")
					.header("Origin", "https://untrusted.example")
					.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void allowedFrontendStillRequiresAuthentication() throws Exception
	{
		this.mockMvc.perform(get("/api/games")
					.header("Origin", "https://development.dag28nzl054yt.amplifyapp.com"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Access-Control-Allow-Origin",
						"https://development.dag28nzl054yt.amplifyapp.com"));
	}

	@Test
	void healthEndpointIsPublic() throws Exception
	{
		this.mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	void currentUserEndpointRejectsUnauthenticatedRequests() throws Exception
	{
		this.mockMvc.perform(get("/api/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void currentUserEndpointAcceptsAuthenticatedRequests() throws Exception
	{
		String subject = "auth0|test-user-" + UUID.randomUUID();
		this.mockMvc.perform(get("/api/me").with(token(subject, "read:profile")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.subject").value(subject));

		var firstProfile = this.appUserRepository
				.findByAuth0IssuerAndAuth0Subject("https://dev-i640yykn7qf8bmaw.us.auth0.com/", subject)
				.orElseThrow();

		this.mockMvc.perform(get("/api/me").with(token(subject, "read:profile")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(firstProfile.getId().toString()));
	}

	@Test
	void currentUserEndpointRejectsTokensWithoutReadProfileScope() throws Exception
	{
		this.mockMvc.perform(get("/api/me").with(jwt().jwt(token -> token
					.subject("auth0|test-user")
					.claim("scope", ""))))
				.andExpect(status().isForbidden());
	}

	@Test
	void boardGameCrudIsLimitedToItsOwner() throws Exception
	{
		String ownerSubject = "auth0|owner-" + UUID.randomUUID();
		String otherSubject = "auth0|other-" + UUID.randomUUID();
		String request = """
				{"name":"Catan","minPlayers":3,"maxPlayers":4,"bestPlayers":"3-4","playTimeMinutes":60,"notes":"Strategy game"}
				""";

		MvcResult created = this.mockMvc.perform(post("/api/games")
					.with(token(ownerSubject, "read:games write:games"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Catan"))
				.andExpect(jsonPath("$.bestPlayers").value("3-4"))
				.andReturn();
		String location = created.getResponse().getHeader("Location");
		String gameId = location.substring(location.lastIndexOf('/') + 1);

		this.mockMvc.perform(get("/api/games").with(token(ownerSubject, "read:games")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Catan"));

		this.mockMvc.perform(get("/api/games").with(token(otherSubject, "read:games")))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));

		this.mockMvc.perform(get("/api/games/{gameId}", gameId)
					.with(token(otherSubject, "read:games")))
				.andExpect(status().isNotFound());

		String updatedRequest = """
				{"name":"Catan Updated","minPlayers":3,"maxPlayers":4,"bestPlayers":"3","playTimeMinutes":75,"notes":"Updated notes"}
				""";
		this.mockMvc.perform(put("/api/games/{gameId}", gameId)
					.with(token(otherSubject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(updatedRequest))
				.andExpect(status().isNotFound());

		this.mockMvc.perform(put("/api/games/{gameId}", gameId)
					.with(token(ownerSubject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(updatedRequest))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Catan Updated"))
				.andExpect(jsonPath("$.bestPlayers").value("3"));

		this.mockMvc.perform(delete("/api/games/{gameId}", gameId)
					.with(token(otherSubject, "write:games")))
				.andExpect(status().isNotFound());

		this.mockMvc.perform(delete("/api/games/{gameId}", gameId)
					.with(token(ownerSubject, "write:games")))
				.andExpect(status().isNoContent());
	}

	@Test
	void boardGameEndpointsRequireTheirScopes() throws Exception
	{
		this.mockMvc.perform(get("/api/games").with(token("auth0|reader", "read:profile")))
				.andExpect(status().isForbidden());

		this.mockMvc.perform(post("/api/games")
					.with(token("auth0|writer", "read:games"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Catan\"}"))
				.andExpect(status().isForbidden());
	}

	private JwtRequestPostProcessor token(String subject, String scopes)
	{
		return jwt().jwt(jwt -> jwt
				.issuer("https://dev-i640yykn7qf8bmaw.us.auth0.com/")
				.subject(subject)
				.claim("scope", scopes));
	}
}
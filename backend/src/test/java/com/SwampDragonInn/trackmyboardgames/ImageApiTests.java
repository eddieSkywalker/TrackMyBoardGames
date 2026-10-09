package com.SwampDragonInn.trackmyboardgames;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
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


// Verifies game cover photos (sent inside the game) and collection background pictures: saving, replacing, removing, file checks,
// and that every picture stays private to its owner.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImageApiTests
{

	// A real 1x1 pixel PNG image.
	private static final byte[] PNG = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

	// Just the start of a JPEG file; the app only checks the first bytes to recognise the format.
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F' };

	@Autowired
	private MockMvc mockMvc;

	// Scenario: the owner creates a game with a cover, swaps it for another picture, then removes it.
	@Test
	void ownerCanAddReplaceAndRemoveACover() throws Exception
	{
		// Arrange: a signed-in owner
		String ownerSubject = "auth0|owner-" + UUID.randomUUID();

		// Act + Assert: creating a game with a PNG cover sends the same picture back
		String gameId = createGame(ownerSubject, dataUrl("image/png", PNG));
		this.mockMvc.perform(get("/api/games/{gameId}", gameId).with(token(ownerSubject, "read:games")))
				.andExpect(jsonPath("$.coverImage").value(dataUrl("image/png", PNG)));

		// Assert: the list includes the cover too
		this.mockMvc.perform(get("/api/games").with(token(ownerSubject, "read:games")))
				.andExpect(jsonPath("$[?(@.id == '" + gameId + "')].coverImage").value(dataUrl("image/png", PNG)));

		// Act + Assert: saving the game with a JPEG replaces the PNG
		this.mockMvc.perform(updateGame(gameId, ownerSubject, "Catan Seafarers", dataUrl("image/jpeg", JPEG)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.coverImage").value(dataUrl("image/jpeg", JPEG)));

		// Act + Assert: saving with no cover removes it, and a search still finds the game
		this.mockMvc.perform(updateGame(gameId, ownerSubject, "Catan Seafarers", null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.coverImage").doesNotExist());
		this.mockMvc.perform(get("/api/games").param("search", "SEAFAR").with(token(ownerSubject, "read:games")))
				.andExpect(jsonPath("$.length()").value(1));
	}

	// Scenario: another user cannot see or replace someone else's cover.
	@Test
	void coversStayPrivateToTheirOwner() throws Exception
	{
		// Arrange: a game with a cover that belongs to the owner
		String ownerSubject = "auth0|owner-" + UUID.randomUUID();
		String otherSubject = "auth0|other-" + UUID.randomUUID();
		String gameId = createGame(ownerSubject, dataUrl("image/png", PNG));

		// Act + Assert: the other user cannot read the game, cannot overwrite it, and does not see it listed
		this.mockMvc.perform(get("/api/games/{gameId}", gameId).with(token(otherSubject, "read:games")))
				.andExpect(status().isNotFound());
		this.mockMvc.perform(updateGame(gameId, otherSubject, "Stolen", dataUrl("image/jpeg", JPEG)))
				.andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/games").with(token(otherSubject, "read:games")))
				.andExpect(jsonPath("$.length()").value(0));

		// Assert: the owner's cover is unchanged
		this.mockMvc.perform(get("/api/games/{gameId}", gameId).with(token(ownerSubject, "read:games")))
				.andExpect(jsonPath("$.coverImage").value(dataUrl("image/png", PNG)));
	}

	// Scenario: covers that are not real pictures, are damaged, or are too large are refused.
	@Test
	void badCoversAreRejected() throws Exception
	{
		// Arrange: a game with no cover, and a file just over the 5 MB limit that starts like a PNG
		String ownerSubject = "auth0|owner-" + UUID.randomUUID();
		String gameId = createGame(ownerSubject, null);
		byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
		System.arraycopy(PNG, 0, tooBig, 0, PNG.length);

		// Act + Assert: text that is not a data URL, broken base64, and a non-image file are all 400
		this.mockMvc.perform(updateGame(gameId, ownerSubject, "Catan", "hello"))
				.andExpect(status().isBadRequest());
		this.mockMvc.perform(updateGame(gameId, ownerSubject, "Catan", "data:image/png;base64,***"))
				.andExpect(status().isBadRequest());
		this.mockMvc.perform(updateGame(gameId, ownerSubject, "Catan", dataUrl("image/png", "not an image".getBytes())))
				.andExpect(status().isBadRequest());

		// Act + Assert: an oversized picture is refused with 413 Payload Too Large
		this.mockMvc.perform(updateGame(gameId, ownerSubject, "Catan", dataUrl("image/png", tooBig)))
				.andExpect(status().isPayloadTooLarge());

		// Assert: none of the attempts left a cover behind
		this.mockMvc.perform(get("/api/games/{gameId}", gameId).with(token(ownerSubject, "read:games")))
				.andExpect(jsonPath("$.coverImage").doesNotExist());
	}
	// Scenario: a user sets, loads, replaces, and removes their collection background,
	// and a different user never sees it.
	@Test
	void collectionBackgroundBelongsToTheUserWhoSetIt() throws Exception
	{
		// Arrange: two users; only the first has a background
		String ownerSubject = "auth0|owner-" + UUID.randomUUID();
		String otherSubject = "auth0|other-" + UUID.randomUUID();
		this.mockMvc.perform(get("/api/collection").with(token(ownerSubject, "read:games")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.backgroundImage").value((Object) null));

		// Act + Assert: saving the collection sets the background and returns it in JSON
		String pngDataUrl = dataUrl("image/png", PNG);
		this.mockMvc.perform(updateCollection(ownerSubject, pngDataUrl))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.backgroundImage").value(pngDataUrl));
		this.mockMvc.perform(get("/api/collection").with(token(ownerSubject, "read:games")))
				.andExpect(jsonPath("$.backgroundImage").value(pngDataUrl));

		// Act + Assert: the other user has no background and cannot see the first user's
		this.mockMvc.perform(get("/api/collection").with(token(otherSubject, "read:games")))
				.andExpect(jsonPath("$.backgroundImage").value((Object) null));

		// Act + Assert: saving a second picture replaces the first one
		String jpegDataUrl = dataUrl("image/jpeg", JPEG);
		this.mockMvc.perform(updateCollection(ownerSubject, jpegDataUrl))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.backgroundImage").value(jpegDataUrl));

		// Act + Assert: removing the other user's own background does not affect the owner's
		this.mockMvc.perform(updateCollection(otherSubject, null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.backgroundImage").value((Object) null));
		this.mockMvc.perform(get("/api/collection").with(token(ownerSubject, "read:games")))
				.andExpect(jsonPath("$.backgroundImage").value(jpegDataUrl));

		// Act + Assert: sending null removes the owner's picture
		this.mockMvc.perform(updateCollection(ownerSubject, null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.backgroundImage").value((Object) null));
	}

	// Scenario: the collection background rejects data that is not a real picture.
	@Test
	void collectionBackgroundRejectsNonImages() throws Exception
	{
		// Act + Assert: an invalid data URL and bytes without a picture signature are refused
		String ownerSubject = "auth0|owner-" + UUID.randomUUID();
		this.mockMvc.perform(updateCollection(ownerSubject, "hello"))
				.andExpect(status().isBadRequest());
		this.mockMvc.perform(updateCollection(ownerSubject, dataUrl("image/png", "not an image".getBytes())))
				.andExpect(status().isBadRequest());
	}

	// Scenario: no token gets a 401, and a token with only read permission cannot change the collection (403).
	@Test
	void imageEndpointsRequireTheirScopes() throws Exception
	{
		// Act + Assert (no Arrange needed): no token, then read-only tokens trying to change things
		this.mockMvc.perform(get("/api/collection"))
				.andExpect(status().isUnauthorized());
		this.mockMvc.perform(updateCollection("auth0|reader", dataUrl("image/png", PNG))
					.with(token("auth0|reader", "read:games")))
				.andExpect(status().isForbidden());
	}

	// Helper: builds the JSON request that saves or removes a collection background.
	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder updateCollection(
			String subject, String backgroundImage)
	{
		String cover = backgroundImage == null ? "null" : "\"" + backgroundImage + "\"";
		return put("/api/collection")
				.with(token(subject, "write:games"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"backgroundImage\":" + cover + "}");
	}

	// Helper: writes picture bytes as text ("data:image/png;base64,..."), the way the browser sends a cover.
	private String dataUrl(String contentType, byte[] bytes)
	{
		return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
	}

	// Helper: the JSON for a game; a null cover is sent as JSON null.
	private String gameJson(String name, String coverImage)
	{
		String cover = coverImage == null ? "null" : "\"" + coverImage + "\"";
		return "{\"name\":\"" + name + "\",\"coverImage\":" + cover + "}";
	}

	// Helper: creates a game for this user and returns its id.
	private String createGame(String subject, String coverImage) throws Exception
	{
		MvcResult created = this.mockMvc.perform(post("/api/games")
					.with(token(subject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(gameJson("Catan", coverImage)))
				.andExpect(status().isCreated())
				.andReturn();
		String location = created.getResponse().getHeader("Location");
		return location.substring(location.lastIndexOf('/') + 1);
	}

	// Helper: builds the request that saves new details (and cover) for an existing game.
	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder updateGame(
			String gameId, String subject, String name, String coverImage)
	{
		return put("/api/games/{gameId}", gameId)
				.with(token(subject, "write:games"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(gameJson(name, coverImage));
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

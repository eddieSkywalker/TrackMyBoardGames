package com.SwampDragonInn.trackmyboardgames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import com.SwampDragonInn.trackmyboardgames.service.UserProfileService;

/**
 * Exercises real API transactions so collection changes must survive beyond the request that saves them.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CollectionManagementApiTests
{

	private static final String ISSUER = "https://example.test/";
	private final List<String> subjects = new ArrayList<>();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserProfileService profiles;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void cleanup()
	{
		for (String subject : this.subjects)
		{
			this.jdbc.update("delete from app_users where auth0_issuer = ? and auth0_subject = ?", ISSUER, subject);
		}
	}

	@Test
	void firstListCreatesOneStartingCollectionAndDoesNotExposeAnotherOwnersCollections() throws Exception
	{
		String subject = newSubject();
		String other = newSubject();
		UUID foreign = defaultCollection(other);

		this.mockMvc.perform(get("/api/collections").with(token(subject, "read:games")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("My collection"))
				.andExpect(jsonPath("$[0].defaultCollection").value(true))
				.andExpect(jsonPath("$[0].backgroundImage").doesNotExist());
		UUID starting = defaultCollection(subject);
		this.mockMvc.perform(get("/api/collections").with(token(subject, "read:games")))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(starting.toString()));
		assertThat(starting).isNotEqualTo(foreign);
	}

	@Test
	void listUsesCreationOrderWithAnIdTieBreaker() throws Exception
	{
		String subject = newSubject();
		defaultCollection(subject);
		UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
		seedCollection(subject, second, "Second");
		seedCollection(subject, first, "First");

		this.mockMvc.perform(get("/api/collections").with(token(subject, "read:games")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].id").value(first.toString()))
				.andExpect(jsonPath("$[1].id").value(second.toString()))
				.andExpect(jsonPath("$[0].defaultCollection").value(false))
				.andExpect(jsonPath("$[2].defaultCollection").value(true));
	}

	@Test
	void renameTrimsTheNameAndPreservesTheBackgroundAndDefault() throws Exception
	{
		String subject = newSubject();
		UUID collectionId = defaultCollection(subject);
		this.jdbc.update("""
				update game_collections set background_content_type = 'image/png',
				background_image = decode('010203', 'hex') where id = ?
				""", collectionId);

		this.mockMvc.perform(put("/api/collections/{id}", collectionId)
					.with(token(subject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  Family games  \"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(collectionId.toString()))
				.andExpect(jsonPath("$.name").value("Family games"))
				.andExpect(jsonPath("$.defaultCollection").value(true));
		this.mockMvc.perform(get("/api/collections").with(token(subject, "read:games")))
				.andExpect(jsonPath("$[0].name").value("Family games"));
		this.mockMvc.perform(get("/api/collection").with(token(subject, "read:games")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.backgroundImage").value("data:image/png;base64,AQID"));
	}

	@Test
	void invalidNamesAndMalformedIdsAreRejectedWithoutChangingTheCollection() throws Exception
	{
		String subject = newSubject();
		UUID id = defaultCollection(subject);
		for (String body : List.of("{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"   \"}",
				"{\"name\":\"" + "a".repeat(201) + "\"}"))
		{
			this.mockMvc.perform(put("/api/collections/{id}", id).with(token(subject, "write:games"))
						.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest());
		}
		this.mockMvc.perform(put("/api/collections/not-a-uuid/default").with(token(subject, "write:games")))
				.andExpect(status().isBadRequest());
		assertThat(this.jdbc.queryForObject("select name from game_collections where id = ?", String.class, id))
				.isEqualTo("My collection");
	}

	@Test
	void renameAcceptsTheMaximumNameLength() throws Exception
	{
		String subject = newSubject();
		UUID id = defaultCollection(subject);
		this.mockMvc.perform(put("/api/collections/{id}", id).with(token(subject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + "a".repeat(200) + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("a".repeat(200)));
	}

	@Test
	void foreignAndMissingCollectionsBothReturnNotFound() throws Exception
	{
		String subject = newSubject();
		UUID originalDefault = defaultCollection(subject);
		UUID foreign = defaultCollection(newSubject());
		for (UUID id : List.of(foreign, UUID.randomUUID()))
		{
			this.mockMvc.perform(put("/api/collections/{id}", id).with(token(subject, "write:games"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Not mine\"}"))
					.andExpect(status().isNotFound());
			this.mockMvc.perform(put("/api/collections/{id}/default", id).with(token(subject, "write:games")))
					.andExpect(status().isNotFound());
		}
		assertThat(defaultCollection(subject)).isEqualTo(originalDefault);
		assertThat(this.jdbc.queryForObject("select name from game_collections where id = ?", String.class, foreign))
				.isEqualTo("My collection");
	}

	@Test
	void changingDefaultPersistsWithoutMovingGamesAndCanBeRepeated() throws Exception
	{
		String subject = newSubject();
		UUID original = defaultCollection(subject);
		UUID next = UUID.randomUUID();
		seedCollection(subject, next, "Travel");
		createGame(subject, "Existing game");

		for (int attempt = 0; attempt < 2; attempt++)
		{
			this.mockMvc.perform(put("/api/collections/{id}/default", next).with(token(subject, "write:games")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(next.toString()))
					.andExpect(jsonPath("$.defaultCollection").value(true));
		}
		assertThat(defaultCollection(subject)).isEqualTo(next);
		this.mockMvc.perform(get("/api/collections").with(token(subject, "read:games")))
				.andExpect(jsonPath("$[0].defaultCollection").value(true))
				.andExpect(jsonPath("$[1].defaultCollection").value(false));
		assertThat(gameCollection(subject, "Existing game")).isEqualTo(original);
		createGame(subject, "New game");
		assertThat(gameCollection(subject, "New game")).isEqualTo(next);
		this.mockMvc.perform(get("/api/games").with(token(subject, "read:games")))
				.andExpect(jsonPath("$.length()").value(2));
		this.jdbc.update("""
				update game_collections set background_content_type = 'image/png',
				background_image = decode('040506', 'hex') where id = ?
				""", next);
		this.mockMvc.perform(get("/api/collection").with(token(subject, "read:games")))
				.andExpect(jsonPath("$.backgroundImage").value("data:image/png;base64,BAUG"));
	}

	@Test
	void allManagementRoutesRequireAuthenticationAndTheMatchingScope() throws Exception
	{
		String subject = newSubject();
		UUID id = defaultCollection(subject);
		this.mockMvc.perform(get("/api/collections")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(put("/api/collections/{id}", id)
					.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Blocked\"}"))
				.andExpect(status().isUnauthorized());
		this.mockMvc.perform(put("/api/collections/{id}/default", id)).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/collections").with(token(subject, "write:games")))
				.andExpect(status().isForbidden());
		this.mockMvc.perform(put("/api/collections/{id}", id).with(token(subject, "read:games"))
					.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Blocked\"}"))
				.andExpect(status().isForbidden());
		this.mockMvc.perform(put("/api/collections/{id}/default", id).with(token(subject, "read:games")))
				.andExpect(status().isForbidden());
		this.mockMvc.perform(get("/api/collections").with(token(subject, "")))
				.andExpect(status().isForbidden());
	}

	@Test
	void additionalCollectionCreationHasNoEndpoint() throws Exception
	{
		String subject = newSubject();
		defaultCollection(subject);
		this.mockMvc.perform(post("/api/collections").with(token(subject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Extra\"}"))
				.andExpect(status().isMethodNotAllowed());
		this.mockMvc.perform(get("/api/collections").with(token(subject, "read:games")))
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void selectedCollectionFiltersGamesAndReceivesNewGamesWithoutChangingDefault() throws Exception
	{
		String subject = newSubject();
		UUID starting = defaultCollection(subject);
		UUID travel = UUID.randomUUID();
		seedCollection(subject, travel, "Travel");
		createGame(subject, "Home game");
		this.mockMvc.perform(post("/api/games").param("collectionId", travel.toString())
					.with(token(subject, "write:games")).contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Travel Azul\"}"))
				.andExpect(status().isCreated());

		assertThat(gameCollection(subject, "Travel Azul")).isEqualTo(travel);
		assertThat(defaultCollection(subject)).isEqualTo(starting);
		this.mockMvc.perform(get("/api/games").param("collectionId", travel.toString())
					.with(token(subject, "read:games")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("Travel Azul"));
		this.mockMvc.perform(get("/api/games").param("collectionId", starting.toString())
					.with(token(subject, "read:games")))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("Home game"));
		this.mockMvc.perform(get("/api/games").param("collectionId", travel.toString()).param("search", " AZUL ")
					.with(token(subject, "read:games")))
				.andExpect(jsonPath("$.length()").value(1));
		this.mockMvc.perform(get("/api/games").param("collectionId", travel.toString()).param("search", "Home")
					.with(token(subject, "read:games")))
				.andExpect(jsonPath("$.length()").value(0));
		this.mockMvc.perform(get("/api/games").with(token(subject, "read:games")))
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void selectedBackgroundCanBeSavedAndRemovedWithoutChangingTheDefaultBackground() throws Exception
	{
		String subject = newSubject();
		UUID starting = defaultCollection(subject);
		UUID travel = UUID.randomUUID();
		seedCollection(subject, travel, "Travel");
		String png = "data:image/png;base64,"
				+ "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
		this.mockMvc.perform(put("/api/collection").param("collectionId", travel.toString())
					.with(token(subject, "write:games")).contentType(MediaType.APPLICATION_JSON)
					.content("{\"backgroundImage\":\"" + png + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.backgroundImage").value(png));
		this.mockMvc.perform(get("/api/collection").param("collectionId", travel.toString())
					.with(token(subject, "read:games")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.backgroundImage").value(png));
		this.mockMvc.perform(get("/api/collection").with(token(subject, "read:games")))
				.andExpect(jsonPath("$.backgroundImage").doesNotExist());
		this.mockMvc.perform(put("/api/collection").param("collectionId", travel.toString())
					.with(token(subject, "write:games")).contentType(MediaType.APPLICATION_JSON)
					.content("{\"backgroundImage\":null}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.backgroundImage").doesNotExist());
		assertThat(defaultCollection(subject)).isEqualTo(starting);
	}

	@Test
	void explicitForeignMissingAndMalformedCollectionIdsNeverFallBackToTheDefault() throws Exception
	{
		String subject = newSubject();
		UUID starting = defaultCollection(subject);
		UUID foreign = defaultCollection(newSubject());
		for (UUID id : List.of(foreign, UUID.randomUUID()))
		{
			this.mockMvc.perform(get("/api/games").param("collectionId", id.toString())
						.with(token(subject, "read:games"))).andExpect(status().isNotFound());
			this.mockMvc.perform(post("/api/games").param("collectionId", id.toString())
						.with(token(subject, "write:games")).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Blocked\"}")).andExpect(status().isNotFound());
			this.mockMvc.perform(get("/api/collection").param("collectionId", id.toString())
						.with(token(subject, "read:games"))).andExpect(status().isNotFound());
			this.mockMvc.perform(put("/api/collection").param("collectionId", id.toString())
						.with(token(subject, "write:games")).contentType(MediaType.APPLICATION_JSON)
						.content("{\"backgroundImage\":null}")).andExpect(status().isNotFound());
		}
		this.mockMvc.perform(get("/api/games").param("collectionId", "not-a-uuid")
					.with(token(subject, "read:games"))).andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/collection").param("collectionId", "not-a-uuid")
					.with(token(subject, "read:games"))).andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/games").with(token(subject, "read:games")))
				.andExpect(jsonPath("$.length()").value(0));
		assertThat(defaultCollection(subject)).isEqualTo(starting);
	}

	private String newSubject()
	{
		String subject = "collection-api-" + UUID.randomUUID();
		this.subjects.add(subject);
		return subject;
	}

	private UUID defaultCollection(String subject)
	{
		UUID owner = this.profiles.findOrCreate(ISSUER, subject).getId();
		return this.jdbc.queryForObject("select default_collection_id from app_users where id = ?", UUID.class, owner);
	}

	/**
	 * Adds an extra collection for testing only; the API does not grant users another collection.
	 */
	private void seedCollection(String subject, UUID id, String name)
	{
		UUID owner = this.profiles.findOrCreate(ISSUER, subject).getId();
		this.jdbc.update("""
				insert into game_collections (id, owner_id, name, created_at) values (?, ?, ?, '2026-01-01')
				""", id, owner, name);
	}

	private void createGame(String subject, String name) throws Exception
	{
		this.mockMvc.perform(post("/api/games").with(token(subject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated());
	}

	private UUID gameCollection(String subject, String name)
	{
		return this.jdbc.queryForObject("""
				select g.collection_id from board_games g join app_users u on u.id = g.owner_id
				where u.auth0_issuer = ? and u.auth0_subject = ? and g.name = ?
				""", UUID.class, ISSUER, subject, name);
	}

	private JwtRequestPostProcessor token(String subject, String scope)
	{
		return jwt().jwt(token -> token.issuer(ISSUER).subject(subject).claim("scope", scope));
	}
}

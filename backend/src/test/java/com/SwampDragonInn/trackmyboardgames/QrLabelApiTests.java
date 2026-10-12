package com.SwampDragonInn.trackmyboardgames;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.jayway.jsonpath.JsonPath;

// Verifies QR labels: creating, listing per user, scanning, revoking, and that they stay private to the owner.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QrLabelApiTests
{

	@Autowired
	private MockMvc mockMvc;

	// The JPG created from a real label token decodes back to a URL whose token returns the expected game.
	@Test
	void jpegQrCodeScansToCorrectGame() throws Exception
	{
		String owner = "auth0|villainous-" + UUID.randomUUID();
		String gameId = createGame(owner, "Villainous", "Disney edition");
		MvcResult created = this.mockMvc.perform(post("/api/games/{id}/labels", gameId)
					.with(token(owner, "write:games")))
				.andExpect(status().isCreated())
				.andReturn();
		String code = JsonPath.<String>read(created.getResponse().getContentAsString(), "$.token");

		String scanUrl = "https://trackmyboardgames.test/scan/" + code;
		var matrix = new QRCodeWriter().encode(scanUrl, BarcodeFormat.QR_CODE, 600, 600);
		BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(matrix);
		ByteArrayOutputStream jpegBytes = new ByteArrayOutputStream();
		ImageIO.write(qrImage, "jpg", jpegBytes);

		BufferedImage jpegImage = ImageIO.read(new ByteArrayInputStream(jpegBytes.toByteArray()));
		BinaryBitmap scanBitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(jpegImage)));
		String decodedUrl = new MultiFormatReader().decode(scanBitmap).getText();
		String scannedToken = URI.create(decodedUrl).getPath().substring("/scan/".length());

		this.mockMvc.perform(get("/api/scan/{token}", scannedToken).with(token(owner, "read:games")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(gameId))
				.andExpect(jsonPath("$.name").value("Villainous"))
				.andExpect(jsonPath("$.edition").value("Disney edition"));
	}

	// Scenario: the owner makes a label, lists it, scans it to find the game, then revokes it and scanning stops working.
	@Test
	void labelLifecycle() throws Exception
	{
		// Arrange
		String owner = "auth0|owner-" + UUID.randomUUID();
		String gameId = createGame(owner);

		// Act + Assert: creating returns a long random token for the game
		MvcResult created = this.mockMvc.perform(post("/api/games/{id}/labels", gameId)
					.with(token(owner, "write:games")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.gameId").value(gameId))
				.andExpect(jsonPath("$.revoked").value(false))
				.andExpect(jsonPath("$.createdAt").exists())
				.andReturn();
		String body = created.getResponse().getContentAsString();
		String labelId = JsonPath.<String>read(body, "$.id");
		String code = JsonPath.<String>read(body, "$.token");
		org.junit.jupiter.api.Assertions.assertEquals(43, code.length());

		// Act + Assert: the user's label list contains it
		this.mockMvc.perform(get("/api/labels").with(token(owner, "read:games")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(labelId));

		// Act + Assert: scanning the token returns the game
		this.mockMvc.perform(get("/api/scan/{token}", code).with(token(owner, "read:games")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(gameId))
				.andExpect(jsonPath("$.name").value("Catan"));

		// Act + Assert: revoking (twice is fine) makes scanning return 404, and the label stays listed as revoked
		this.mockMvc.perform(delete("/api/games/{id}/labels/{labelId}", gameId, labelId)
					.with(token(owner, "write:games")))
				.andExpect(status().isNoContent());
		this.mockMvc.perform(delete("/api/games/{id}/labels/{labelId}", gameId, labelId)
					.with(token(owner, "write:games")))
				.andExpect(status().isNoContent());
		this.mockMvc.perform(get("/api/scan/{token}", code).with(token(owner, "read:games")))
				.andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/labels").with(token(owner, "read:games")))
				.andExpect(jsonPath("$[0].revoked").value(true))
				.andExpect(jsonPath("$[0].revokedAt").exists());
	}

	// Scenario: another user cannot make labels for, scan, list, or revoke the owner's labels.
	@Test
	void labelsArePrivateToTheOwner() throws Exception
	{
		// Arrange
		String owner = "auth0|owner-" + UUID.randomUUID();
		String other = "auth0|other-" + UUID.randomUUID();
		String gameId = createGame(owner);
		MvcResult created = this.mockMvc.perform(post("/api/games/{id}/labels", gameId)
					.with(token(owner, "write:games")))
				.andExpect(status().isCreated())
				.andReturn();
		String body = created.getResponse().getContentAsString();

		// Act + Assert: all of the other user's attempts look like "not found"
		this.mockMvc.perform(post("/api/games/{id}/labels", gameId).with(token(other, "write:games")))
				.andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/scan/{token}", JsonPath.<String>read(body, "$.token")).with(token(other, "read:games")))
				.andExpect(status().isNotFound());
		this.mockMvc.perform(delete("/api/games/{id}/labels/{labelId}", gameId, JsonPath.<String>read(body, "$.id"))
					.with(token(other, "write:games")))
				.andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/labels").with(token(other, "read:games")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	// Scenario: an unknown token is 404, and the routes require sign-in and the right permissions.
	@Test
	void unknownTokensAndMissingPermissionsAreRejected() throws Exception
	{
		String user = "auth0|user-" + UUID.randomUUID();
		String gameId = createGame(user);

		this.mockMvc.perform(get("/api/scan/{token}", "not-a-real-token").with(token(user, "read:games")))
				.andExpect(status().isNotFound());

		this.mockMvc.perform(get("/api/labels")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/scan/{token}", "x")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(post("/api/games/{id}/labels", gameId).with(token(user, "read:games")))
				.andExpect(status().isForbidden());
		this.mockMvc.perform(get("/api/labels").with(token(user, "write:games")))
				.andExpect(status().isForbidden());
	}

	// Helper: creates a game for the given user and returns its id.
	private String createGame(String subject) throws Exception
	{
		return createGame(subject, "Catan", null);
	}

	private String createGame(String subject, String name, String edition) throws Exception
	{
		String requestBody = edition == null
				? "{\"name\":\"" + name + "\"}"
				: "{\"name\":\"" + name + "\",\"edition\":\"" + edition + "\"}";
		MvcResult created = this.mockMvc.perform(post("/api/games")
					.with(token(subject, "write:games"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody))
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
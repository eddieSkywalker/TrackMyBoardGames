package com.SwampDragonInn.trackmyboardgames;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthEndpointIsPublic() throws Exception {
		this.mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	void currentUserEndpointRejectsUnauthenticatedRequests() throws Exception {
		this.mockMvc.perform(get("/api/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void currentUserEndpointAcceptsAuthenticatedRequests() throws Exception {
		this.mockMvc.perform(get("/api/me").with(jwt().jwt(token -> token
					.subject("auth0|test-user")
					.claim("scope", "read:profile"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.subject").value("auth0|test-user"));
	}

	@Test
	void currentUserEndpointRejectsTokensWithoutReadProfileScope() throws Exception {
		this.mockMvc.perform(get("/api/me").with(jwt().jwt(token -> token
					.subject("auth0|test-user")
					.claim("scope", ""))))
				.andExpect(status().isForbidden());
	}
}
package com.SwampDragonInn.trackmyboardgames;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "ok");
	}

	@GetMapping("/me")
	public Map<String, String> currentUser(Authentication authentication) {
		return Map.of("subject", authentication.getName());
	}
}
package com.SwampDragonInn.trackmyboardgames;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// Defines which requests are public and which require Auth0 authentication and API permissions.
@Configuration
public class SecurityConfig
{

	// Allow the health check, then require the matching token permission for each protected API route.
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception
	{
		return http
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/api/health").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/me").hasAuthority("SCOPE_read:profile")
						.requestMatchers(HttpMethod.GET, "/api/games", "/api/games/**", "/api/collection/**", "/api/labels", "/api/scan/**", "/api/statistics").hasAuthority("SCOPE_read:games")
						.requestMatchers(HttpMethod.POST, "/api/games", "/api/games/**").hasAuthority("SCOPE_write:games")
						.requestMatchers(HttpMethod.PUT, "/api/games/**", "/api/collection/**").hasAuthority("SCOPE_write:games")
						.requestMatchers(HttpMethod.DELETE, "/api/games/**", "/api/collection/**").hasAuthority("SCOPE_write:games")
						.requestMatchers("/api/**").authenticated()
						.anyRequest().permitAll())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
				.build();
	}

	// Each environment supplies the exact frontend origins allowed to call this API.
	@Bean
	CorsConfigurationSource corsConfigurationSource(
			@Value("${app.cors.allowed-origins}") List<String> allowedOrigins)
	{
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}
}
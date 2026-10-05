package com.SwampDragonInn.trackmyboardgames.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SwampDragonInn.trackmyboardgames.service.BoardGameService;

import jakarta.validation.Valid;

// Entry point for game API requests: Spring validates the Auth0 token and supplies its identity here.
// The controller passes that identity and the request data to BoardGameService.
// The service finds or creates the local profile, then uses BoardGameRepository to access games
// owned by that profile in PostgreSQL.
@Validated
@RestController
@RequestMapping("/api/games")
public class BoardGameController
{

	private final BoardGameService boardGameService;

	public BoardGameController(BoardGameService boardGameService)
	{
		this.boardGameService = boardGameService;
	}

	// List only the signed-in user's games, with an optional name search.
	@GetMapping
	public List<BoardGameResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String search)
	{
		return this.boardGameService.findAll(jwt.getIssuer().toString(), jwt.getSubject(), search);
	}

	// Get one game only if it belongs to the signed-in user.
	@GetMapping("/{gameId}")
	public BoardGameResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId)
	{
		return this.boardGameService.findById(jwt.getIssuer().toString(), jwt.getSubject(), gameId);
	}

	// Create a game owned by the signed-in user and return its new URL.
	@PostMapping
	public ResponseEntity<BoardGameResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BoardGameRequest request)
	{
		BoardGameResponse game = this.boardGameService.create(jwt.getIssuer().toString(), jwt.getSubject(), request);
		return ResponseEntity.created(URI.create("/api/games/" + game.id())).body(game);
	}

	// Replace a game’s details, but only when it belongs to the signed-in user.
	@PutMapping("/{gameId}")
	public BoardGameResponse update(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID gameId,
		@Valid @RequestBody BoardGameRequest request)
	{
		return this.boardGameService.update(jwt.getIssuer().toString(), jwt.getSubject(), gameId, request);
	}

	// Delete a game only when it belongs to the signed-in user.
	@DeleteMapping("/{gameId}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId)
	{
		this.boardGameService.delete(jwt.getIssuer().toString(), jwt.getSubject(), gameId);
		return ResponseEntity.noContent().build();
	}
}
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
import org.springframework.web.bind.annotation.RestController;

import com.SwampDragonInn.trackmyboardgames.service.GamePlayService;

import jakarta.validation.Valid;

// Entry point for play history requests, nested under the game they belong to.
// It reuses the games scopes: reading plays needs read:games and changing them needs write:games.
@Validated
@RestController
@RequestMapping("/api/games/{gameId}/plays")
public class GamePlayController
{

    private final GamePlayService gamePlayService;

    public GamePlayController(GamePlayService gamePlayService)
    {
        this.gamePlayService = gamePlayService;
    }

    // List a game's plays, newest first.
    @GetMapping
    public List<GamePlayResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId)
    {
        return this.gamePlayService.findAll(jwt.getIssuer().toString(), jwt.getSubject(), gameId);
    }

    // Get one play of a game.
    @GetMapping("/{playId}")
    public GamePlayResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId,
            @PathVariable UUID playId)
    {
        return this.gamePlayService.findById(jwt.getIssuer().toString(), jwt.getSubject(), gameId, playId);
    }

    // Record a play for a game and return its new URL.
    @PostMapping
    public ResponseEntity<GamePlayResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId,
            @Valid @RequestBody GamePlayRequest request)
    {
        GamePlayResponse play = this.gamePlayService.create(jwt.getIssuer().toString(), jwt.getSubject(), gameId,
                request);
        return ResponseEntity.created(URI.create("/api/games/" + gameId + "/plays/" + play.id())).body(play);
    }

    // Replace a play's details.
    @PutMapping("/{playId}")
    public GamePlayResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId,
            @PathVariable UUID playId, @Valid @RequestBody GamePlayRequest request)
    {
        return this.gamePlayService.update(jwt.getIssuer().toString(), jwt.getSubject(), gameId, playId, request);
    }

    // Delete a play.
    @DeleteMapping("/{playId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId,
            @PathVariable UUID playId)
    {
        this.gamePlayService.delete(jwt.getIssuer().toString(), jwt.getSubject(), gameId, playId);
        return ResponseEntity.noContent().build();
    }
}

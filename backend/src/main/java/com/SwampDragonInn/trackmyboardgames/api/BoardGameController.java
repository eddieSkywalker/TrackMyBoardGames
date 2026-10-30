package com.SwampDragonInn.trackmyboardgames.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import javax.swing.Spring;

import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SwampDragonInn.trackmyboardgames.service.BoardGameService;
import com.SwampDragonInn.trackmyboardgames.service.CollectionGameLimitException;

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

    /**
     * Sends the game-limit explanation to the page so it can keep the form open and show what happened.
     */
    @ExceptionHandler(CollectionGameLimitException.class)
    public ProblemDetail gameLimitReached(CollectionGameLimitException exception)
    {
        return exception.getBody();
    }

    /**
     * Lists owned games, optionally limited to a chosen collection and name search.
     */
    @GetMapping
    public List<BoardGameResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID collectionId)
    {
        return this.boardGameService.findAll(jwt.getIssuer().toString(), jwt.getSubject(), search, collectionId);
    }

    // Get one game only if it belongs to the signed-in user.
    @GetMapping("/{gameId}")
    public BoardGameResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId)
    {
        return this.boardGameService.findById(jwt.getIssuer().toString(), jwt.getSubject(), gameId);
    }

    /**
     * Validates the submitted game and saves it in the chosen owned collection, returning its new URL.
     * Without a collection ID, the saved default is used.
     */
    @PostMapping
    public ResponseEntity<BoardGameResponse> create(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody BoardGameRequest request,
        @RequestParam(required = false) UUID collectionId)
    {
        BoardGameResponse game = this.boardGameService.create(jwt.getIssuer().toString(), jwt.getSubject(), request, collectionId);

        return ResponseEntity.created(URI.create("/api/games/" + game.id())).body(game);
    }

    // Replace a game’s details, but only when it belongs to the signed-in user.
    @PutMapping("/{gameId}")
    public BoardGameResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId, @Valid @RequestBody BoardGameRequest request)
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
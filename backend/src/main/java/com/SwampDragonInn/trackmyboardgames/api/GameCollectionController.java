package com.SwampDragonInn.trackmyboardgames.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.SwampDragonInn.trackmyboardgames.service.GameCollectionService;

import jakarta.validation.Valid;

// Entry point for the signed-in user's collection and its shelf background picture.
// Reading needs read:games and changing the background needs write:games, the same as the games themselves.
@Validated
@RestController
@RequestMapping("/api/collection")
public class GameCollectionController
{

    private final GameCollectionService gameCollectionService;

    public GameCollectionController(GameCollectionService gameCollectionService)
    {
        this.gameCollectionService = gameCollectionService;
    }

    /**
     * Reads the selected collection's background, or the saved default when no ID was supplied.
     */
    @GetMapping
    public CollectionResponse get(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) UUID collectionId)
    {
        String backgroundImage = this.gameCollectionService.getBackgroundImage(
                jwt.getIssuer().toString(), jwt.getSubject(), collectionId);
        return new CollectionResponse(backgroundImage);
    }

    /**
     * Saves or removes the selected collection's background without changing the saved default.
     */
    @PutMapping
    public CollectionResponse update(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CollectionResponse request, @RequestParam(required = false) UUID collectionId)
    {
        String backgroundImage = this.gameCollectionService.saveBackground(
                jwt.getIssuer().toString(), jwt.getSubject(), request.backgroundImage(), collectionId);
        return new CollectionResponse(backgroundImage);
    }
}

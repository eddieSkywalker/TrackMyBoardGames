package com.SwampDragonInn.trackmyboardgames.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    // Return the collection settings and its background picture (if one has been set).
    @GetMapping
    public CollectionResponse get(@AuthenticationPrincipal Jwt jwt)
    {
        String backgroundImage = this.gameCollectionService.getBackgroundImage(
                jwt.getIssuer().toString(), jwt.getSubject());
        return new CollectionResponse(backgroundImage);
    }

    // Save or remove the background in the collection JSON; null removes the current picture.
    @PutMapping
    public CollectionResponse update(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CollectionResponse request)
    {
        String backgroundImage = this.gameCollectionService.saveBackground(
                jwt.getIssuer().toString(), jwt.getSubject(), request.backgroundImage());
        return new CollectionResponse(backgroundImage);
    }
}

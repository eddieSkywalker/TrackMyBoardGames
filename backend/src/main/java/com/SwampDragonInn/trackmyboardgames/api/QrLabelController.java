package com.SwampDragonInn.trackmyboardgames.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.SwampDragonInn.trackmyboardgames.service.QrLabelService;

// Entry point for QR label requests. It reuses the games scopes: reading needs read:games, changing needs write:games.
@RestController
public class QrLabelController
{

    private final QrLabelService qrLabelService;

    public QrLabelController(QrLabelService qrLabelService)
    {
        this.qrLabelService = qrLabelService;
    }

    // List all of the signed-in user's labels.
    @GetMapping("/api/labels")
    public List<QrLabelResponse> list(@AuthenticationPrincipal Jwt jwt)
    {
        return this.qrLabelService.findAll(jwt.getIssuer().toString(), jwt.getSubject());
    }

    // Make a new label for a game and return its new URL.
    @PostMapping("/api/games/{gameId}/labels")
    public ResponseEntity<QrLabelResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId)
    {
        QrLabelResponse label = this.qrLabelService.create(jwt.getIssuer().toString(), jwt.getSubject(), gameId);
        return ResponseEntity.created(URI.create("/api/games/" + gameId + "/labels/" + label.id())).body(label);
    }

    // Revoke a label so scanning it no longer works.
    @DeleteMapping("/api/games/{gameId}/labels/{labelId}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId,
            @PathVariable UUID labelId)
    {
        this.qrLabelService.revoke(jwt.getIssuer().toString(), jwt.getSubject(), gameId, labelId);
        return ResponseEntity.noContent().build();
    }

    // Look up the game behind a scanned token (only for its owner).
    @GetMapping("/api/scan/{token}")
    public BoardGameResponse scan(@AuthenticationPrincipal Jwt jwt, @PathVariable String token)
    {
        return this.qrLabelService.resolve(jwt.getIssuer().toString(), jwt.getSubject(), token);
    }
}
package com.SwampDragonInn.trackmyboardgames.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.SwampDragonInn.trackmyboardgames.service.GameCollectionService;

import jakarta.validation.Valid;

/**
 * Manages collections already owned by the signed-in user; additional collection creation is not enabled.
 */
@RestController
@RequestMapping("/api/collections")
public class CollectionManagementController
{

	private final GameCollectionService gameCollectionService;

	/**
	 * Supplies the service that checks ownership before reading or changing a collection.
	 */
	public CollectionManagementController(GameCollectionService gameCollectionService)
	{
		this.gameCollectionService = gameCollectionService;
	}

	/**
	 * Lists the user's collections and marks the saved default.
	 */
	@GetMapping
	public List<CollectionSummaryResponse> list(@AuthenticationPrincipal Jwt jwt)
	{
		return this.gameCollectionService.list(jwt.getIssuer().toString(), jwt.getSubject());
	}

	/**
	 * Changes a collection's name without changing its games or background.
	 */
	@PutMapping("/{collectionId}")
	public CollectionSummaryResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID collectionId,
			@Valid @RequestBody CollectionNameRequest request)
	{
		return this.gameCollectionService.rename(jwt.getIssuer().toString(), jwt.getSubject(), collectionId, request);
	}

	/**
	 * Saves which owned collection to use by default; this does not move existing games.
	 */
	@PutMapping("/{collectionId}/default")
	public CollectionSummaryResponse setDefault(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID collectionId)
	{
		return this.gameCollectionService.setDefault(jwt.getIssuer().toString(), jwt.getSubject(), collectionId);
	}
}

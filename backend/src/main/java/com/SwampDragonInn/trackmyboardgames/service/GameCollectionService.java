package com.SwampDragonInn.trackmyboardgames.service;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.SwampDragonInn.trackmyboardgames.api.CollectionNameRequest;
import com.SwampDragonInn.trackmyboardgames.api.CollectionSummaryResponse;
import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.GameCollection;
import com.SwampDragonInn.trackmyboardgames.repository.GameCollectionRepository;

/**
 * Manages owned collections and the saved default's shelf background. Every lookup checks the signed-in owner.
 */
@Service
public class GameCollectionService
{

	private final UserProfileService userProfileService;
	private final ImageUploadChecker imageUploadChecker;
	private final GameCollectionRepository gameCollectionRepository;

	/**
	 * Supplies profile setup, image checks, and access to the user's collections.
	 */
	public GameCollectionService(UserProfileService userProfileService, ImageUploadChecker imageUploadChecker,
			GameCollectionRepository gameCollectionRepository)
	{
		this.userProfileService = userProfileService;
		this.imageUploadChecker = imageUploadChecker;
		this.gameCollectionRepository = gameCollectionRepository;
	}

	/**
	 * Lists only this user's collections, marking the saved default without changing it.
	 */
	@Transactional
	public List<CollectionSummaryResponse> list(String subjectIssuer, String subject)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		return this.gameCollectionRepository.findAllByOwner_IdOrderByCreatedAtAscIdAsc(owner.getId()).stream()
				.map(collection -> toSummary(collection, owner))
				.toList();
	}

	/**
	 * Renames an owned collection; a missing or foreign collection gets the same not-found response.
	 */
	@Transactional
	public CollectionSummaryResponse rename(String subjectIssuer, String subject, UUID collectionId,
			CollectionNameRequest request)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		GameCollection collection = findOwnedCollection(collectionId, owner.getId());
		collection.rename(request.name().trim());
		return toSummary(collection, owner);
	}

	/**
	 * Saves the default preference only. Existing games stay where they are, and reads remain owner-wide for now.
	 */
	@Transactional
	public CollectionSummaryResponse setDefault(String subjectIssuer, String subject, UUID collectionId)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);
		GameCollection collection = findOwnedCollection(collectionId, owner.getId());
		owner.setDefaultCollection(collection);
		return toSummary(collection, owner);
	}

	/**
	 * Finds a collection only for its owner; missing and someone else's collections both return not found.
	 */
	private GameCollection findOwnedCollection(UUID collectionId, UUID ownerId)
	{
		return this.gameCollectionRepository.findByIdAndOwner_Id(collectionId, ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection not found."));
	}

	/**
	 * Prepares the collection's name and saved-default marker without sending its picture or games.
	 */
	private CollectionSummaryResponse toSummary(GameCollection collection, AppUser owner)
	{
		return new CollectionSummaryResponse(collection.getId(), collection.getName(),
				collection.getId().equals(owner.getDefaultCollection().getId()));
	}

	/**
	 * Returns the default collection's background as image text, or null when none has been set.
	 */
	@Transactional
	public String getBackgroundImage(String subjectIssuer, String subject)
	{
		GameCollection collection = findOrCreateCollection(subjectIssuer, subject);
		if (collection.getBackgroundImage() == null)
		{
			return null;
		}

		String base64 = Base64.getEncoder().encodeToString(collection.getBackgroundImage());
		return "data:" + collection.getBackgroundContentType() + ";base64," + base64;
	}

	/**
	 * Checks and saves a background for the default collection; null or blank text removes it.
	 */
	@Transactional
	public String saveBackground(String subjectIssuer, String subject, String backgroundImage)
	{
		GameCollection collection = findOrCreateCollection(subjectIssuer, subject);
		if (backgroundImage == null || backgroundImage.isBlank())
		{
			collection.removeBackground();
			return null;
		}

		StoredImage image = this.imageUploadChecker.checkDataUrl(backgroundImage);
		collection.setBackground(image.contentType(), image.data());
		return "data:" + image.contentType() + ";base64," + Base64.getEncoder().encodeToString(image.data());
	}

	/**
	 * Ensures profile setup is complete, then returns its saved default collection.
	 */
	private GameCollection findOrCreateCollection(String subjectIssuer, String subject)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);

		return owner.getDefaultCollection();
	}
}

package com.SwampDragonInn.trackmyboardgames.service;

import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.SwampDragonInn.trackmyboardgames.domain.AppUser;
import com.SwampDragonInn.trackmyboardgames.domain.GameCollection;

// Looks after the signed-in user's collection and its shelf background picture. Everything is found through
// the signed-in user, so there is no way to ask for someone else's collection.
@Service
public class GameCollectionService
{

	private final UserProfileService userProfileService;
	private final ImageUploadChecker imageUploadChecker;

	public GameCollectionService(UserProfileService userProfileService, ImageUploadChecker imageUploadChecker)
	{
		this.userProfileService = userProfileService;
		this.imageUploadChecker = imageUploadChecker;
	}

	// Return the background as text for the collection JSON, or null when none has been set.
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

	// Validate and save a background sent as text, or remove it when the request sends null.
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

	// Profile initialization ensures the owner has a saved starting/default collection.
	private GameCollection findOrCreateCollection(String subjectIssuer, String subject)
	{
		AppUser owner = this.userProfileService.findOrCreate(subjectIssuer, subject);

		return owner.getDefaultCollection();
	}
}

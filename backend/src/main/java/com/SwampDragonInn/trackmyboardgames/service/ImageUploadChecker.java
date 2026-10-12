package com.SwampDragonInn.trackmyboardgames.service;

import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

// Checks a supplied picture before it is stored: not empty, not too large, and really a JPEG, PNG, or WebP.
// The format is found by reading the first bytes, because the content type in the data URL can be faked.
// SVG is deliberately not allowed since it can contain scripts.
@Component
public class ImageUploadChecker
{

	static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
	private static final String BASE64_MARKER = ";base64,";

	// Check a picture sent as text ("data:image/jpeg;base64,..."), as game covers and collection backgrounds are.
	public StoredImage checkDataUrl(String dataUrl)
	{
		if (dataUrl == null)
		{
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The image is not valid.");
		}

		int start = dataUrl.indexOf(BASE64_MARKER);
		if (!dataUrl.startsWith("data:") || start < 0)
		{
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The image is not valid.");
		}

		try
		{
			return check(Base64.getDecoder().decode(dataUrl.substring(start + BASE64_MARKER.length())));
		}
		catch (IllegalArgumentException exception)
		{
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The image is not valid.");
		}
	}

	// The shared checks for both entry points: not empty, not over 5 MB, and a recognised picture format.
	private StoredImage check(byte[] bytes)
	{
		if (bytes.length == 0)
		{
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an image file to upload.");
		}

		if (bytes.length > MAX_IMAGE_BYTES)
		{
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Images can be at most 5 MB.");
		}

		String contentType = detectContentType(bytes);
		if (contentType == null)
		{
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, or WebP images are supported.");
		}

		return new StoredImage(contentType, bytes);
	}

	// Each image format starts with a fixed "signature"; compare the start of the file against the known ones.
	private String detectContentType(byte[] bytes)
	{
		if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF))
		{
			return "image/jpeg";
		}

		if (startsWith(bytes, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
		{
			return "image/png";
		}

		// WebP is "RIFF", then a 4-byte size, then "WEBP".
		boolean isRiff = startsWith(bytes, 0, 'R', 'I', 'F', 'F');
		boolean isWebp = startsWith(bytes, 8, 'W', 'E', 'B', 'P');
		if (isRiff && isWebp)
		{
			return "image/webp";
		}

		return null;
	}

	// True when the bytes starting at the given position match the expected values exactly.
	private boolean startsWith(byte[] bytes, int offset, int... expected)
	{
		if (bytes.length < offset + expected.length)
		{
			return false;
		}

		for (int i = 0; i < expected.length; i++)
		{
			if ((bytes[offset + i] & 0xFF) != expected[i])
			{
				return false;
			}
		}

		return true;
	}
}

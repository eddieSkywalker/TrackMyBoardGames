package com.SwampDragonInn.trackmyboardgames.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Explains why a collection cannot accept another game at its current allowance.
 */
public class CollectionGameLimitException extends ResponseStatusException
{

	public CollectionGameLimitException(int gameLimit)
	{
		super(HttpStatus.CONFLICT, "This collection has reached its limit of " + gameLimit
				+ " games. Remove a game or upgrade the collection before adding another.");
	}
}

package com.SwampDragonInn.trackmyboardgames.service;

// An image that passed checking: the real format (found by reading the file, not trusting the upload's label)
// and its bytes. Also used to hand a stored image back to the controller.
public record StoredImage(String contentType, byte[] data)
{
}

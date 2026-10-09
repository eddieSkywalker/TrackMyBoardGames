package com.SwampDragonInn.trackmyboardgames.api;

import jakarta.validation.constraints.Size;

// Describes the collection data sent to the frontend, including its optional background picture.
// backgroundImage uses the format "data:image/jpeg;base64,..."; null means there is no picture.
public record CollectionResponse(@Size(max = 7_000_000) String backgroundImage)
{
}

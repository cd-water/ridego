package com.cdwater.ridego.trip.lifecycle;

import java.time.Instant;
import java.util.UUID;

public record TripEvent(
        String type,
        UUID tripId,
        String cityId,
        Instant occurredAt,
        Object payload
) {
}

package com.cdwater.ridego.location.trail;

import java.time.Instant;
import java.util.UUID;

public record TripPosition(
        UUID tripId,
        String driverId,
        String cityId,
        double lat,
        double lng,
        Instant recordedAt
) {
}

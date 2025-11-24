package com.cdwater.ridego.contracts;

import java.time.Instant;

public record TripCompletedPayload(
        String tripId,
        String riderId,
        String driverId,
        Instant startedAt,
        Instant completedAt,
        double pickupLat,
        double pickupLng,
        double dropoffLat,
        double dropoffLng
) {
}

package com.cdwater.ridego.trip.trip;

import java.time.Instant;
import java.util.UUID;

public record TripRecord(
        UUID id,
        String cityId,
        String riderId,
        String driverId,
        String state,
        double pickupLat,
        double pickupLng,
        double dropoffLat,
        double dropoffLng,
        Instant requestedAt,
        Instant matchingDeadline,
        Instant acceptedAt,
        Instant arrivedAt,
        Instant startedAt,
        Instant completedAt
) {
}

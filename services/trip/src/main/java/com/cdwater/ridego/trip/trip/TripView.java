package com.cdwater.ridego.trip.trip;

import java.time.Instant;
import java.util.UUID;

public record TripView(
        UUID id,
        String cityId,
        String riderId,
        String driverId,
        String state,
        double pickupLat,
        double pickupLng,
        double dropoffLat,
        double dropoffLng,
        Instant updatedAt
) {
}

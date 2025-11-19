package com.cdwater.ridego.contracts;

public record TripRequestedPayload(
        String tripId,
        String riderId,
        String cityId,
        double pickupLat,
        double pickupLng,
        double dropoffLat,
        double dropoffLng
) {
}

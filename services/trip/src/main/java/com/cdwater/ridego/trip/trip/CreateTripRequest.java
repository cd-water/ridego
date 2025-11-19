package com.cdwater.ridego.trip.trip;

public record CreateTripRequest(
        String cityId,
        double pickupLat,
        double pickupLng,
        double dropoffLat,
        double dropoffLng
) {
}

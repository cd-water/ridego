package com.cdwater.ridego.contracts;

public record TripDriverCancelledPayload(String tripId, String driverId, String reason) {
}

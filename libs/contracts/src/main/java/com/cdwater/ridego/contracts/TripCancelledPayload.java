package com.cdwater.ridego.contracts;

public record TripCancelledPayload(String tripId, String actor, String reason) {
}

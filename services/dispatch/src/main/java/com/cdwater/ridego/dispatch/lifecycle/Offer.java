package com.cdwater.ridego.dispatch.lifecycle;

import java.time.Instant;
import java.util.UUID;

public class Offer {

    UUID id;
    UUID tripId;
    String driverId;
    String cityId;
    OfferState state;
    Instant createdAt;
    Instant expiresAt;
    Instant respondedAt;

    public Offer(UUID id, UUID tripId, String driverId, String cityId,
                 OfferState state, Instant createdAt, Instant expiresAt, Instant respondedAt) {
        this.id = id;
        this.tripId = tripId;
        this.driverId = driverId;
        this.cityId = cityId;
        this.state = state;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.respondedAt = respondedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public String getDriverId() {
        return driverId;
    }

    public String getCityId() {
        return cityId;
    }

    public OfferState getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}

package com.cdwater.ridego.trip.lifecycle;

import java.time.Instant;
import java.util.UUID;

/**
 * Write-side snapshot of a Trip. Mutable, but only {@link TripLifecycle}
 * (same package) advances it; adapters construct and read it.
 */
public class Trip {

    UUID id;
    String cityId;
    String riderId;
    String driverId;
    String offerId;
    TripState state;
    double pickupLat;
    double pickupLng;
    double dropoffLat;
    double dropoffLng;
    Instant requestedAt;
    Instant matchingDeadline;
    Instant matchingStartedAt;
    Instant acceptedAt;
    Instant arrivedAt;
    Instant startedAt;
    Instant completedAt;
    Instant cancelledAt;
    Instant unmatchedAt;
    String cancelActor;
    String cancelReason;
    long version;

    public Trip(UUID id, String cityId, String riderId, String driverId, String offerId,
                TripState state, double pickupLat, double pickupLng,
                double dropoffLat, double dropoffLng,
                Instant requestedAt, Instant matchingDeadline, Instant matchingStartedAt,
                Instant acceptedAt, Instant arrivedAt, Instant startedAt,
                Instant completedAt, Instant cancelledAt, Instant unmatchedAt,
                String cancelActor, String cancelReason, long version) {
        this.id = id;
        this.cityId = cityId;
        this.riderId = riderId;
        this.driverId = driverId;
        this.offerId = offerId;
        this.state = state;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.dropoffLat = dropoffLat;
        this.dropoffLng = dropoffLng;
        this.requestedAt = requestedAt;
        this.matchingDeadline = matchingDeadline;
        this.matchingStartedAt = matchingStartedAt;
        this.acceptedAt = acceptedAt;
        this.arrivedAt = arrivedAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.cancelledAt = cancelledAt;
        this.unmatchedAt = unmatchedAt;
        this.cancelActor = cancelActor;
        this.cancelReason = cancelReason;
        this.version = version;
    }

    public static Trip requested(UUID id, String cityId, String riderId,
                                 double pickupLat, double pickupLng,
                                 double dropoffLat, double dropoffLng,
                                 Instant now, Instant deadline) {
        return new Trip(id, cityId, riderId, null, null, TripState.REQUESTED,
                pickupLat, pickupLng, dropoffLat, dropoffLng,
                now, deadline, null, null, null, null, null, null, null,
                null, null, 0);
    }

    public UUID getId() {
        return id;
    }

    public String getCityId() {
        return cityId;
    }

    public String getRiderId() {
        return riderId;
    }

    public String getDriverId() {
        return driverId;
    }

    public String getOfferId() {
        return offerId;
    }

    public TripState getState() {
        return state;
    }

    public double getPickupLat() {
        return pickupLat;
    }

    public double getPickupLng() {
        return pickupLng;
    }

    public double getDropoffLat() {
        return dropoffLat;
    }

    public double getDropoffLng() {
        return dropoffLng;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getMatchingDeadline() {
        return matchingDeadline;
    }

    public Instant getMatchingStartedAt() {
        return matchingStartedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getUnmatchedAt() {
        return unmatchedAt;
    }

    public String getCancelActor() {
        return cancelActor;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public long getVersion() {
        return version;
    }
}

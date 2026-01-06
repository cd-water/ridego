package com.cdwater.ridego.trip.read;

import com.cdwater.ridego.contracts.TripRequestedPayload;
import com.cdwater.ridego.trip.lifecycle.TripState;
import com.cdwater.ridego.trip.trip.TripView;

import java.util.Optional;
import java.util.UUID;

public interface TripReadStore {

    void create(TripRequestedPayload trip);

    void updateState(UUID tripId, TripState state, String driverId, boolean clearDriver);

    Optional<TripView> find(UUID tripId);
}

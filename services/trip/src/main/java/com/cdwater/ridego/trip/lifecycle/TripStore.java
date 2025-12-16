package com.cdwater.ridego.trip.lifecycle;

import java.util.Optional;
import java.util.UUID;

public interface TripStore {

    Optional<Trip> load(UUID id);

    void insert(Trip trip);

    /**
     * Persists the mutated Trip if it is still in {@code expectedState}.
     *
     * @return false when the state CAS lost (another command advanced the Trip)
     */
    boolean save(Trip trip, String expectedState);
}

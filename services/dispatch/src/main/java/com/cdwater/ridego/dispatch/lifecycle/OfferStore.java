package com.cdwater.ridego.dispatch.lifecycle;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface OfferStore {

    Optional<Offer> load(UUID offerId);

    List<Offer> pendingForDriver(String driverId);

    List<Offer> findExpired(Instant now, int limit);

    List<Offer> findActiveForTrip(UUID tripId);

    Set<String> attemptedDrivers(UUID tripId);

    /**
     * @return false when the PG unique index rejected a second PENDING offer for the driver
     */
    boolean insert(Offer offer);

    /**
     * @return false when the state CAS lost
     */
    boolean save(Offer offer, String expectedState);
}

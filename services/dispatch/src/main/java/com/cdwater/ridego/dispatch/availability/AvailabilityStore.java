package com.cdwater.ridego.dispatch.availability;

import java.util.Optional;

public interface AvailabilityStore {

    Optional<Availability> load(String driverId);

    void insert(Availability availability);

    boolean save(Availability availability,
                 AvailabilityState expectedState,
                 Presence expectedPresence);
}

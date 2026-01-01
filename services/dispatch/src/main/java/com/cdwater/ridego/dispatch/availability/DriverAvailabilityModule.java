package com.cdwater.ridego.dispatch.availability;

import com.cdwater.ridego.dispatch.lifecycle.DriverAvailability;

import java.util.Optional;

public class DriverAvailabilityModule implements DriverAvailability {

    private final AvailabilityStore store;

    public DriverAvailabilityModule(AvailabilityStore store) {
        this.store = store;
    }

    @Override
    public boolean isIdle(String driverId, String cityId) {
        return store.load(driverId)
                .filter(a -> a.state == AvailabilityState.IDLE)
                .filter(a -> a.presence == Presence.ONLINE)
                .filter(a -> cityId.equals(a.cityId))
                .isPresent();
    }

    @Override
    public boolean markOffered(String driverId) {
        Optional<Availability> loaded = store.load(driverId);
        if (loaded.isEmpty()) {
            return false;
        }
        Availability availability = loaded.get();
        if (availability.state != AvailabilityState.IDLE
                || availability.presence != Presence.ONLINE) {
            return false;
        }
        AvailabilityState expectedState = availability.state;
        Presence expectedPresence = availability.presence;
        availability.state = AvailabilityState.OFFERED;
        return store.save(availability, expectedState, expectedPresence);
    }

    @Override
    public boolean markOnTrip(String driverId) {
        Optional<Availability> loaded = store.load(driverId);
        if (loaded.isEmpty()) {
            return false;
        }
        Availability availability = loaded.get();
        if (availability.state != AvailabilityState.OFFERED
                || availability.presence != Presence.ONLINE) {
            return false;
        }
        AvailabilityState expectedState = availability.state;
        Presence expectedPresence = availability.presence;
        availability.state = AvailabilityState.ON_TRIP;
        return store.save(availability, expectedState, expectedPresence);
    }

    @Override
    public boolean release(String driverId) {
        Optional<Availability> loaded = store.load(driverId);
        if (loaded.isEmpty()) {
            return true;
        }
        Availability availability = loaded.get();
        if (availability.state != AvailabilityState.OFFERED
                && availability.state != AvailabilityState.ON_TRIP) {
            return true;
        }
        AvailabilityState expectedState = availability.state;
        Presence expectedPresence = availability.presence;
        availability.state = availability.presence == Presence.ONLINE
                ? AvailabilityState.IDLE
                : AvailabilityState.OFFLINE;
        return store.save(availability, expectedState, expectedPresence);
    }

    @Override
    public boolean markOnline(String driverId, String cityId) {
        Optional<Availability> loaded = store.load(driverId);
        if (loaded.isEmpty()) {
            store.insert(new Availability(driverId, cityId,
                    AvailabilityState.IDLE, Presence.ONLINE));
            return true;
        }
        Availability availability = loaded.get();
        AvailabilityState expectedState = availability.state;
        Presence expectedPresence = availability.presence;
        availability.presence = Presence.ONLINE;
        availability.cityId = cityId;
        if (availability.state == AvailabilityState.OFFLINE) {
            availability.state = AvailabilityState.IDLE;
        }
        return store.save(availability, expectedState, expectedPresence);
    }

    @Override
    public boolean markOffline(String driverId, String cityId) {
        Optional<Availability> loaded = store.load(driverId);
        if (loaded.isEmpty()) {
            store.insert(new Availability(driverId, cityId,
                    AvailabilityState.OFFLINE, Presence.OFFLINE));
            return true;
        }
        Availability availability = loaded.get();
        AvailabilityState expectedState = availability.state;
        Presence expectedPresence = availability.presence;
        availability.presence = Presence.OFFLINE;
        availability.cityId = cityId;
        if (availability.state == AvailabilityState.IDLE
                || availability.state == AvailabilityState.OFFERED) {
            availability.state = AvailabilityState.OFFLINE;
        }
        return store.save(availability, expectedState, expectedPresence);
    }
}

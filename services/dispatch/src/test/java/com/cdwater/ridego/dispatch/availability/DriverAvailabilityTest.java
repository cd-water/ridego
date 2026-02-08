package com.cdwater.ridego.dispatch.availability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverAvailabilityTest {

    private InMemoryAvailabilityStore store;
    private DriverAvailabilityModule module;

    @BeforeEach
    void setUp() {
        store = new InMemoryAvailabilityStore();
        module = new DriverAvailabilityModule(store);
    }

    @Test
    void markOnlineInsertsIdleOnline() {
        assertTrue(module.markOnline("driver-1", "city-1"));

        assertEquals(AvailabilityState.IDLE, store.stateOf("driver-1"));
        assertEquals(Presence.ONLINE, store.presenceOf("driver-1"));
    }

    @Test
    void offlineThenOnlineReturnsToIdle() {
        module.markOnline("driver-1", "city-1");

        assertTrue(module.markOffline("driver-1", "city-1"));
        assertEquals(AvailabilityState.OFFLINE, store.stateOf("driver-1"));
        assertEquals(Presence.OFFLINE, store.presenceOf("driver-1"));

        assertTrue(module.markOnline("driver-1", "city-1"));
        assertEquals(AvailabilityState.IDLE, store.stateOf("driver-1"));
        assertEquals(Presence.ONLINE, store.presenceOf("driver-1"));
    }

    @Test
    void onTripDriverGoingOfflineIsReleasedToOffline() {
        module.markOnline("driver-1", "city-1");
        module.markOffered("driver-1");
        module.markOnTrip("driver-1");

        module.markOffline("driver-1", "city-1");
        assertEquals(AvailabilityState.ON_TRIP, store.stateOf("driver-1"));
        assertEquals(Presence.OFFLINE, store.presenceOf("driver-1"));

        assertTrue(module.release("driver-1"));
        assertEquals(AvailabilityState.OFFLINE, store.stateOf("driver-1"),
                "an offline driver must not be resurrected as IDLE");
        assertEquals(Presence.OFFLINE, store.presenceOf("driver-1"));
    }

    @Test
    void offeredDriverGoingOfflineKeepsOfferState() {
        module.markOnline("driver-1", "city-1");
        module.markOffered("driver-1");

        module.markOffline("driver-1", "city-1");
        assertEquals(AvailabilityState.OFFERED, store.stateOf("driver-1"));
        assertEquals(Presence.OFFLINE, store.presenceOf("driver-1"));

        module.markOnline("driver-1", "city-1");
        assertEquals(AvailabilityState.OFFERED, store.stateOf("driver-1"));
        assertEquals(Presence.ONLINE, store.presenceOf("driver-1"));

        assertTrue(module.release("driver-1"));
        assertEquals(AvailabilityState.IDLE, store.stateOf("driver-1"));
    }

    @Test
    void markOfferedRequiresIdleOnline() {
        assertFalse(module.markOffered("missing"));

        module.markOnline("driver-1", "city-1");
        module.markOffline("driver-1", "city-1");
        assertFalse(module.markOffered("driver-1"));

        module.markOnline("driver-1", "city-1");
        assertTrue(module.markOffered("driver-1"));
        assertEquals(AvailabilityState.OFFERED, store.stateOf("driver-1"));
    }

    @Test
    void markOnTripRequiresOfferedOnline() {
        module.markOnline("driver-1", "city-1");
        assertFalse(module.markOnTrip("driver-1"));

        module.markOffered("driver-1");
        assertTrue(module.markOnTrip("driver-1"));
        assertEquals(AvailabilityState.ON_TRIP, store.stateOf("driver-1"));
    }

    @Test
    void releaseIsIdempotentForTerminalStates() {
        assertTrue(module.release("missing"));

        module.markOnline("driver-1", "city-1");
        assertTrue(module.release("driver-1"));
        assertEquals(AvailabilityState.IDLE, store.stateOf("driver-1"));
    }

    @Test
    void casFailureReturnsFalse() {
        module.markOnline("driver-1", "city-1");
        store.failNextSave = true;

        assertFalse(module.markOffered("driver-1"));
        assertEquals(AvailabilityState.IDLE, store.stateOf("driver-1"));
    }

    @Test
    void isIdleRequiresMatchingCity() {
        module.markOnline("driver-1", "city-1");

        assertTrue(module.isIdle("driver-1", "city-1"));
        assertFalse(module.isIdle("driver-1", "city-2"));
    }

    static final class InMemoryAvailabilityStore implements AvailabilityStore {
        private final Map<String, Availability> rows = new HashMap<>();
        private final Map<String, AvailabilityState> states = new HashMap<>();
        private final Map<String, Presence> presences = new HashMap<>();
        boolean failNextSave;

        AvailabilityState stateOf(String driverId) {
            return states.get(driverId);
        }

        Presence presenceOf(String driverId) {
            return presences.get(driverId);
        }

        @Override
        public Optional<Availability> load(String driverId) {
            return Optional.ofNullable(rows.get(driverId));
        }

        @Override
        public void insert(Availability availability) {
            rows.put(availability.getDriverId(), availability);
            states.put(availability.getDriverId(), availability.getState());
            presences.put(availability.getDriverId(), availability.getPresence());
        }

        @Override
        public boolean save(Availability availability,
                            AvailabilityState expectedState,
                            Presence expectedPresence) {
            if (failNextSave) {
                failNextSave = false;
                return false;
            }
            String driverId = availability.getDriverId();
            if (states.get(driverId) != expectedState
                    || presences.get(driverId) != expectedPresence) {
                return false;
            }
            states.put(driverId, availability.getState());
            presences.put(driverId, availability.getPresence());
            return true;
        }
    }
}

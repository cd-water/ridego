package com.cdwater.ridego.trip.lifecycle;

import com.cdwater.ridego.contracts.EventTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final UUID OFFER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String DRIVER = "driver-1";

    private InMemoryTripStore store;
    private RecordingSink sink;
    private TripLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        store = new InMemoryTripStore();
        sink = new RecordingSink();
        lifecycle = new TripLifecycle(store, sink, Clock.fixed(NOW, ZoneOffset.UTC), 90);
    }

    @Test
    void requestStartsInRequestedState() {
        UUID id = request();

        assertEquals(TripState.REQUESTED, store.load(id).orElseThrow().getState());
        assertEquals(NOW, store.load(id).orElseThrow().getRequestedAt());
        assertEquals(List.of(EventTypes.TRIP_REQUESTED), sink.types());
    }

    @Test
    void happyPathEmitsOneEventPerTransition() {
        UUID id = request();
        lifecycle.beginMatching(id);
        lifecycle.offerTo(id, OFFER_ID, DRIVER);
        lifecycle.acceptOffer(id, OFFER_ID, DRIVER);
        lifecycle.arrive(id, DRIVER);
        lifecycle.start(id, DRIVER);
        lifecycle.complete(id, DRIVER);

        assertEquals(TripState.COMPLETED, store.load(id).orElseThrow().getState());
        assertEquals(List.of(
                EventTypes.TRIP_REQUESTED,
                EventTypes.TRIP_MATCHING_STARTED,
                EventTypes.TRIP_OFFERED,
                EventTypes.TRIP_ACCEPTED,
                EventTypes.TRIP_ARRIVED,
                EventTypes.TRIP_STARTED,
                EventTypes.TRIP_COMPLETED), sink.types());
    }

    @Test
    void duplicateMatchingStartedIsNoop() {
        UUID id = request();
        lifecycle.beginMatching(id);
        lifecycle.beginMatching(id);

        assertEquals(1, sink.count(EventTypes.TRIP_MATCHING_STARTED));
    }

    @Test
    void wrongDriverCannotArrive() {
        UUID id = accepted();

        assertThrows(TripForbiddenException.class, () -> lifecycle.arrive(id, "driver-2"));
        assertEquals(0, sink.count(EventTypes.TRIP_ARRIVED));
        assertEquals(TripState.ACCEPTED, store.load(id).orElseThrow().getState());
    }

    @Test
    void riderCannotCancelInProgress() {
        UUID id = accepted();
        lifecycle.arrive(id, DRIVER);
        lifecycle.start(id, DRIVER);

        assertThrows(TripConflictException.class,
                () -> lifecycle.cancel(id, ActorType.RIDER, "rider-1", "changed_mind"));
        assertEquals(TripState.IN_PROGRESS, store.load(id).orElseThrow().getState());
        assertEquals(0, sink.count(EventTypes.TRIP_CANCELLED));
    }

    @Test
    void driverCancelFromArrivedRedispatches() {
        UUID id = accepted();
        lifecycle.arrive(id, DRIVER);

        lifecycle.cancel(id, ActorType.DRIVER, DRIVER, "no_show");

        Trip trip = store.load(id).orElseThrow();
        assertEquals(TripState.MATCHING, trip.getState());
        assertNull(trip.getDriverId());
        assertNull(trip.getOfferId());
        assertEquals(1, sink.count(EventTypes.TRIP_DRIVER_CANCELLED));
        assertEquals(1, sink.count(EventTypes.TRIP_MATCHING_RESTARTED));
    }

    @Test
    void acceptAfterCancelEmitsRejectedWithoutStateChange() {
        UUID id = request();
        lifecycle.beginMatching(id);
        lifecycle.offerTo(id, OFFER_ID, DRIVER);
        lifecycle.cancel(id, ActorType.RIDER, "rider-1", "changed_mind");

        lifecycle.acceptOffer(id, OFFER_ID, DRIVER);

        assertEquals(TripState.CANCELLED, store.load(id).orElseThrow().getState());
        assertEquals(EventTypes.TRIP_ACCEPT_REJECTED, sink.lastType());
    }

    @Test
    void casFailureEmitsNoEvents() {
        UUID id = request();
        sink.clear();
        store.failNextSave = true;

        assertThrows(TripConflictException.class, () -> lifecycle.beginMatching(id));
        assertTrue(sink.events.isEmpty());
    }

    @Test
    void matchingWindowExpiryMarksUnmatched() {
        UUID id = request();
        lifecycle.beginMatching(id);
        lifecycle.markUnmatched(id);
        lifecycle.markUnmatched(id);

        assertEquals(TripState.UNMATCHED, store.load(id).orElseThrow().getState());
        assertEquals(1, sink.count(EventTypes.TRIP_UNMATCHED));
    }

    @Test
    void requestedTripCanTimeOutToUnmatched() {
        UUID id = request();

        lifecycle.markUnmatched(id);

        assertEquals(TripState.UNMATCHED, store.load(id).orElseThrow().getState());
        assertEquals(1, sink.count(EventTypes.TRIP_UNMATCHED));
    }

    @Test
    void invalidCoordinatesAreRejected() {
        assertThrows(TripBadRequestException.class,
                () -> lifecycle.request("rider-1", "city-1", 100, 121.47, 31.24, 121.49));
        assertTrue(sink.events.isEmpty());
    }

    private UUID request() {
        return lifecycle.request("rider-1", "city-1", 31.2304, 121.4737, 31.2400, 121.4900);
    }

    private UUID accepted() {
        UUID id = request();
        lifecycle.beginMatching(id);
        lifecycle.offerTo(id, OFFER_ID, DRIVER);
        lifecycle.acceptOffer(id, OFFER_ID, DRIVER);
        return id;
    }

    static final class InMemoryTripStore implements TripStore {
        private final Map<UUID, Trip> trips = new HashMap<>();
        private final Map<UUID, TripState> persistedStates = new HashMap<>();
        boolean failNextSave;

        @Override
        public Optional<Trip> load(UUID id) {
            return Optional.ofNullable(trips.get(id));
        }

        @Override
        public void insert(Trip trip) {
            trips.put(trip.getId(), trip);
            persistedStates.put(trip.getId(), trip.getState());
        }

        @Override
        public boolean save(Trip trip, String expectedState) {
            if (failNextSave) {
                failNextSave = false;
                return false;
            }
            TripState current = persistedStates.get(trip.getId());
            if (current == null || !current.wireValue().equals(expectedState)) {
                return false;
            }
            persistedStates.put(trip.getId(), trip.getState());
            return true;
        }
    }

    static final class RecordingSink implements TripEventSink {
        private final List<TripEvent> events = new ArrayList<>();

        @Override
        public void append(TripEvent event) {
            events.add(event);
        }

        List<String> types() {
            return events.stream().map(TripEvent::type).toList();
        }

        String lastType() {
            return events.get(events.size() - 1).type();
        }

        long count(String type) {
            return events.stream().filter(e -> e.type().equals(type)).count();
        }

        void clear() {
            events.clear();
        }
    }
}

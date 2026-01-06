package com.cdwater.ridego.trip.read;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.contracts.TripCancelledPayload;
import com.cdwater.ridego.contracts.TripCompletedPayload;
import com.cdwater.ridego.contracts.TripDriverCancelledPayload;
import com.cdwater.ridego.contracts.TripDriverPayload;
import com.cdwater.ridego.contracts.TripLifecyclePayload;
import com.cdwater.ridego.contracts.TripOfferedPayload;
import com.cdwater.ridego.contracts.TripRequestedPayload;
import com.cdwater.ridego.trip.lifecycle.TripState;
import com.cdwater.ridego.trip.trip.TripView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripReadModelTest {

    private static final UUID TRIP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RecordingStore store;
    private TripReadModel model;

    @BeforeEach
    void setUp() {
        store = new RecordingStore();
        model = new TripReadModel(store, objectMapper);
    }

    @Test
    void requestedCreatesReadRow() {
        model.apply(event(EventTypes.TRIP_REQUESTED, requested()));

        assertEquals(TRIP_ID.toString(), store.created.tripId());
        assertEquals("rider-1", store.created.riderId());
        assertTrue(store.updates.isEmpty());
    }

    @Test
    void stateEventsUpdateToTheirTargetState() {
        model.apply(event(EventTypes.TRIP_MATCHING_STARTED, new TripLifecyclePayload(TRIP_ID.toString())));
        assertUpdate(TripState.MATCHING, null, false);

        model.apply(event(EventTypes.TRIP_OFFERED,
                new TripOfferedPayload(TRIP_ID.toString(), "driver-1", UUID.randomUUID().toString())));
        assertUpdate(TripState.OFFERED, "driver-1", false);

        model.apply(event(EventTypes.TRIP_ACCEPTED, new TripDriverPayload(TRIP_ID.toString(), "driver-1")));
        assertUpdate(TripState.ACCEPTED, "driver-1", false);

        model.apply(event(EventTypes.TRIP_ARRIVED, new TripDriverPayload(TRIP_ID.toString(), "driver-1")));
        assertUpdate(TripState.ARRIVED, "driver-1", false);

        model.apply(event(EventTypes.TRIP_STARTED, new TripDriverPayload(TRIP_ID.toString(), "driver-1")));
        assertUpdate(TripState.IN_PROGRESS, "driver-1", false);

        model.apply(event(EventTypes.TRIP_COMPLETED, new TripCompletedPayload(
                TRIP_ID.toString(), "rider-1", "driver-1", NOW, NOW,
                31.2304, 121.4737, 31.24, 121.49)));
        assertUpdate(TripState.COMPLETED, "driver-1", false);

        model.apply(event(EventTypes.TRIP_CANCELLED,
                new TripCancelledPayload(TRIP_ID.toString(), "rider", "changed_mind")));
        assertUpdate(TripState.CANCELLED, null, false);

        model.apply(event(EventTypes.TRIP_UNMATCHED, new TripLifecyclePayload(TRIP_ID.toString())));
        assertUpdate(TripState.UNMATCHED, null, false);
    }

    @Test
    void restartedAndDriverCancelledClearDriver() {
        model.apply(event(EventTypes.TRIP_MATCHING_RESTARTED, requested()));
        assertUpdate(TripState.MATCHING, null, true);

        model.apply(event(EventTypes.TRIP_DRIVER_CANCELLED,
                new TripDriverCancelledPayload(TRIP_ID.toString(), "driver-1", "no_show")));
        assertUpdate(TripState.MATCHING, "driver-1", true);
    }

    @Test
    void acceptRejectedDoesNotTouchReadModel() {
        model.apply(event(EventTypes.TRIP_ACCEPT_REJECTED,
                new OfferLifecyclePayload(UUID.randomUUID().toString(), TRIP_ID.toString(), "driver-1", null)));

        assertTrue(store.updates.isEmpty());
        assertNull(store.created);
    }

    @Test
    void findDelegatesToStore() {
        TripView view = new TripView(TRIP_ID, "city-1", "rider-1", "driver-1", "completed",
                31.2304, 121.4737, 31.24, 121.49, NOW);
        store.view = Optional.of(view);

        assertEquals(Optional.of(view), model.find(TRIP_ID));
    }

    private void assertUpdate(TripState state, String driverId, boolean clearDriver) {
        Update update = store.updates.get(store.updates.size() - 1);
        assertEquals(TRIP_ID, update.tripId());
        assertEquals(state, update.state());
        assertEquals(driverId, update.driverId());
        assertEquals(clearDriver, update.clearDriver());
    }

    private TripRequestedPayload requested() {
        return new TripRequestedPayload(TRIP_ID.toString(), "rider-1", "city-1",
                31.2304, 121.4737, 31.24, 121.49);
    }

    private EventEnvelope<JsonNode> event(String type, Object payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(), type, 1, NOW,
                TRIP_ID.toString(), "city-1", TRIP_ID.toString(), null,
                tree(payload));
    }

    private JsonNode tree(Object payload) {
        return objectMapper.readTree(objectMapper.writeValueAsString(payload));
    }

    record Update(UUID tripId, TripState state, String driverId, boolean clearDriver) {
    }

    static final class RecordingStore implements TripReadStore {
        TripRequestedPayload created;
        final List<Update> updates = new ArrayList<>();
        Optional<TripView> view = Optional.empty();

        @Override
        public void create(TripRequestedPayload trip) {
            created = trip;
        }

        @Override
        public void updateState(UUID tripId, TripState state, String driverId, boolean clearDriver) {
            updates.add(new Update(tripId, state, driverId, clearDriver));
        }

        @Override
        public Optional<TripView> find(UUID tripId) {
            return view;
        }
    }
}

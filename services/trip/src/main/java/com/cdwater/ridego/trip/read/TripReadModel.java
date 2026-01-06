package com.cdwater.ridego.trip.read;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.TripCompletedPayload;
import com.cdwater.ridego.contracts.TripDriverCancelledPayload;
import com.cdwater.ridego.contracts.TripDriverPayload;
import com.cdwater.ridego.contracts.TripOfferedPayload;
import com.cdwater.ridego.contracts.TripRequestedPayload;
import com.cdwater.ridego.trip.lifecycle.TripState;
import com.cdwater.ridego.trip.trip.TripView;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

@Component
public class TripReadModel {

    private final TripReadStore store;
    private final ObjectMapper objectMapper;

    public TripReadModel(TripReadStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public void apply(EventEnvelope<JsonNode> event) {
        if (EventTypes.TRIP_REQUESTED.equals(event.eventType())) {
            store.create(objectMapper.treeToValue(
                    event.payload(), TripRequestedPayload.class));
            return;
        }

        TripState state = TripState.stateOfEvent(event.eventType());
        if (state == null) {
            return;
        }

        boolean clearDriver = EventTypes.TRIP_MATCHING_RESTARTED.equals(event.eventType())
                || EventTypes.TRIP_DRIVER_CANCELLED.equals(event.eventType());

        store.updateState(
                UUID.fromString(event.aggregateId()),
                state,
                driverIdOf(event),
                clearDriver);
    }

    public Optional<TripView> find(UUID tripId) {
        return store.find(tripId);
    }

    private String driverIdOf(EventEnvelope<JsonNode> event) {
        return switch (event.eventType()) {
            case EventTypes.TRIP_OFFERED -> objectMapper.treeToValue(
                    event.payload(), TripOfferedPayload.class).driverId();
            case EventTypes.TRIP_ACCEPTED, EventTypes.TRIP_ARRIVED, EventTypes.TRIP_STARTED ->
                    objectMapper.treeToValue(event.payload(), TripDriverPayload.class).driverId();
            case EventTypes.TRIP_COMPLETED -> objectMapper.treeToValue(
                    event.payload(), TripCompletedPayload.class).driverId();
            case EventTypes.TRIP_DRIVER_CANCELLED -> objectMapper.treeToValue(
                    event.payload(), TripDriverCancelledPayload.class).driverId();
            default -> null;
        };
    }
}

package com.cdwater.ridego.location.trail;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.contracts.TripCompletedPayload;
import com.cdwater.ridego.contracts.TripDriverCancelledPayload;
import com.cdwater.ridego.contracts.TripDriverPayload;
import com.cdwater.ridego.location.geo.ActiveTripRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TripTrailListener {

    private final EventEnvelopeCodec codec;
    private final ObjectMapper objectMapper;
    private final ActiveTripRegistry activeTrips;

    public TripTrailListener(EventEnvelopeCodec codec,
                             ObjectMapper objectMapper,
                             ActiveTripRegistry activeTrips) {
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.activeTrips = activeTrips;
    }

    @KafkaListener(topics = Topics.TRIP_EVENTS, groupId = "location-trip-events")
    public void onTripEvent(String message) {
        EventEnvelope<JsonNode> event = codec.decode(message);

        switch (event.eventType()) {
            case EventTypes.TRIP_STARTED -> {
                TripDriverPayload payload = objectMapper.treeToValue(
                        event.payload(), TripDriverPayload.class);
                activeTrips.start(payload.tripId(), payload.driverId());
            }
            case EventTypes.TRIP_COMPLETED -> {
                TripCompletedPayload payload = objectMapper.treeToValue(
                        event.payload(), TripCompletedPayload.class);
                activeTrips.endByDriver(payload.driverId());
            }
            case EventTypes.TRIP_DRIVER_CANCELLED -> {
                TripDriverCancelledPayload payload = objectMapper.treeToValue(
                        event.payload(), TripDriverCancelledPayload.class);
                activeTrips.endByTrip(payload.tripId());
            }
            case EventTypes.TRIP_CANCELLED ->
                    activeTrips.endByTrip(event.aggregateId());
            default -> {
            }
        }
    }
}

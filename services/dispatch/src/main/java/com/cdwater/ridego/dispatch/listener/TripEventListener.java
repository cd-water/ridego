package com.cdwater.ridego.dispatch.listener;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.contracts.TripCompletedPayload;
import com.cdwater.ridego.contracts.TripDriverCancelledPayload;
import com.cdwater.ridego.dispatch.matching.MatchingService;
import com.cdwater.ridego.dispatch.tripclosure.TripClosureTransaction;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class TripEventListener {

    private final EventEnvelopeCodec codec;
    private final ObjectMapper objectMapper;
    private final MatchingService matchingService;
    private final TripClosureTransaction tripClosure;

    public TripEventListener(EventEnvelopeCodec codec,
                             ObjectMapper objectMapper,
                             MatchingService matchingService,
                             TripClosureTransaction tripClosure) {
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.matchingService = matchingService;
        this.tripClosure = tripClosure;
    }

    @KafkaListener(topics = Topics.TRIP_EVENTS, groupId = "dispatch-trip-events")
    public void onTripEvent(String message) {
        EventEnvelope<JsonNode> event = codec.decode(message);

        switch (event.eventType()) {
            case EventTypes.TRIP_REQUESTED, EventTypes.TRIP_MATCHING_RESTARTED ->
                    matchingService.onTripRequested(event);
            case EventTypes.TRIP_COMPLETED -> {
                TripCompletedPayload payload = objectMapper.treeToValue(
                        event.payload(), TripCompletedPayload.class);
                tripClosure.close(UUID.fromString(payload.tripId()), payload.driverId());
            }
            case EventTypes.TRIP_ACCEPT_REJECTED -> {
                OfferLifecyclePayload payload = objectMapper.treeToValue(
                        event.payload(), OfferLifecyclePayload.class);
                tripClosure.releaseAccepted(
                        UUID.fromString(payload.offerId()), payload.driverId());
            }
            case EventTypes.TRIP_DRIVER_CANCELLED -> {
                TripDriverCancelledPayload payload = objectMapper.treeToValue(
                        event.payload(), TripDriverCancelledPayload.class);
                tripClosure.releaseDriver(
                        UUID.fromString(payload.tripId()), payload.driverId());
            }
            case EventTypes.TRIP_CANCELLED, EventTypes.TRIP_UNMATCHED ->
                    tripClosure.close(UUID.fromString(event.aggregateId()), null);
            default -> {
            }
        }
    }
}

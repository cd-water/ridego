package com.cdwater.ridego.trip.listener;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.trip.trip.TripTransitionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DispatchEventListener {

    private final ObjectMapper objectMapper;
    private final TripTransitionService transitions;

    public DispatchEventListener(ObjectMapper objectMapper, TripTransitionService transitions) {
        this.objectMapper = objectMapper;
        this.transitions = transitions;
    }

    @KafkaListener(topics = Topics.DISPATCH_EVENTS, groupId = "trip-dispatch-events")
    public void onDispatchEvent(String message) {
        EventEnvelope<JsonNode> event = objectMapper.readValue(
                message, new TypeReference<EventEnvelope<JsonNode>>() {
                });

        switch (event.eventType()) {
            case EventTypes.MATCHING_STARTED -> transitions.matchingStarted(event.aggregateId());
            case EventTypes.OFFER_CREATED -> transitions.offerCreated(payload(event));
            case EventTypes.OFFER_ACCEPTED -> transitions.offerAccepted(payload(event));
            case EventTypes.OFFER_EXPIRED, EventTypes.OFFER_REJECTED ->
                    transitions.offerClosed(payload(event));
            default -> {
            }
        }
    }

    private OfferLifecyclePayload payload(EventEnvelope<JsonNode> event) {
        return objectMapper.treeToValue(event.payload(), OfferLifecyclePayload.class);
    }
}

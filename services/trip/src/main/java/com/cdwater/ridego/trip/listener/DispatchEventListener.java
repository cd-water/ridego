package com.cdwater.ridego.trip.listener;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.trip.lifecycle.TripBadRequestException;
import com.cdwater.ridego.trip.lifecycle.TripConflictException;
import com.cdwater.ridego.trip.lifecycle.TripForbiddenException;
import com.cdwater.ridego.trip.lifecycle.TripLifecycle;
import com.cdwater.ridego.trip.lifecycle.TripNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class DispatchEventListener {

    private static final Logger log = LoggerFactory.getLogger(DispatchEventListener.class);

    private final EventEnvelopeCodec codec;
    private final ObjectMapper objectMapper;
    private final TripLifecycle lifecycle;
    private final Counter rejected;

    public DispatchEventListener(EventEnvelopeCodec codec,
                                 ObjectMapper objectMapper,
                                 TripLifecycle lifecycle,
                                 MeterRegistry meterRegistry) {
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.lifecycle = lifecycle;
        this.rejected = Counter.builder("ridego.trip.intents.rejected").register(meterRegistry);
    }

    @KafkaListener(topics = Topics.DISPATCH_EVENTS, groupId = "trip-dispatch-events")
    public void onDispatchEvent(String message) {
        EventEnvelope<JsonNode> event = codec.decode(message);

        switch (event.eventType()) {
            case EventTypes.MATCHING_STARTED -> apply(() ->
                    lifecycle.beginMatching(UUID.fromString(event.aggregateId())));
            case EventTypes.OFFER_CREATED -> apply(() -> {
                OfferLifecyclePayload payload = payload(event);
                lifecycle.offerTo(UUID.fromString(payload.tripId()),
                        UUID.fromString(payload.offerId()), payload.driverId());
            });
            case EventTypes.OFFER_ACCEPTED -> apply(() -> {
                OfferLifecyclePayload payload = payload(event);
                lifecycle.acceptOffer(UUID.fromString(payload.tripId()),
                        UUID.fromString(payload.offerId()), payload.driverId());
            });
            case EventTypes.OFFER_EXPIRED, EventTypes.OFFER_REJECTED -> apply(() -> {
                OfferLifecyclePayload payload = payload(event);
                lifecycle.offerFailed(UUID.fromString(payload.tripId()),
                        UUID.fromString(payload.offerId()), payload.driverId());
            });
            default -> {
            }
        }
    }

    private void apply(Runnable intent) {
        try {
            intent.run();
        } catch (TripConflictException | TripNotFoundException
                 | TripForbiddenException | TripBadRequestException e) {
            rejected.increment();
            log.info("dispatch intent rejected: {}", e.getMessage());
        }
    }

    private OfferLifecyclePayload payload(EventEnvelope<JsonNode> event) {
        return objectMapper.treeToValue(event.payload(), OfferLifecyclePayload.class);
    }
}

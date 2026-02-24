package com.cdwater.ridego.trip.listener;

import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.messaging.EventConsumer;
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

import java.util.UUID;

@Component
public class DispatchEventListener {

    private static final Logger log = LoggerFactory.getLogger(DispatchEventListener.class);

    private final TripLifecycle lifecycle;
    private final Counter rejected;
    private final EventConsumer consumer;

    public DispatchEventListener(EventEnvelopeCodec codec,
                                 TripLifecycle lifecycle,
                                 MeterRegistry meterRegistry) {
        this.lifecycle = lifecycle;
        this.rejected = Counter.builder("ridego.trip.intents.rejected").register(meterRegistry);
        this.consumer = new EventConsumer(codec, "trip-dispatch-events")
                .on(EventTypes.MATCHING_STARTED, event -> apply(() ->
                        lifecycle.beginMatching(UUID.fromString(event.aggregateId()))))
                .on(EventTypes.OFFER_CREATED, OfferLifecyclePayload.class, event ->
                        apply(() -> {
                            OfferLifecyclePayload payload = event.payload();
                            lifecycle.offerTo(UUID.fromString(payload.tripId()),
                                    UUID.fromString(payload.offerId()), payload.driverId());
                        }))
                .on(EventTypes.OFFER_ACCEPTED, OfferLifecyclePayload.class, event ->
                        apply(() -> {
                            OfferLifecyclePayload payload = event.payload();
                            lifecycle.acceptOffer(UUID.fromString(payload.tripId()),
                                    UUID.fromString(payload.offerId()), payload.driverId());
                        }))
                .on(EventTypes.OFFER_EXPIRED, OfferLifecyclePayload.class, event ->
                        apply(() -> offerFailed(event.payload())))
                .on(EventTypes.OFFER_REJECTED, OfferLifecyclePayload.class, event ->
                        apply(() -> offerFailed(event.payload())));
    }

    @KafkaListener(topics = Topics.DISPATCH_EVENTS, groupId = "trip-dispatch-events")
    public void onDispatchEvent(String message) {
        consumer.consume(message);
    }

    private void offerFailed(OfferLifecyclePayload payload) {
        lifecycle.offerFailed(UUID.fromString(payload.tripId()),
                UUID.fromString(payload.offerId()), payload.driverId());
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
}

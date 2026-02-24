package com.cdwater.ridego.dispatch.listener;

import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.contracts.TripDriverCancelledPayload;
import com.cdwater.ridego.contracts.TripRequestedPayload;
import com.cdwater.ridego.dispatch.lifecycle.OfferLifecycle;
import com.cdwater.ridego.dispatch.matching.MatchingService;
import com.cdwater.ridego.messaging.EventConsumer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TripEventListener {

    private final OfferLifecycle offerLifecycle;
    private final EventConsumer consumer;

    public TripEventListener(EventEnvelopeCodec codec,
                             MatchingService matchingService,
                             OfferLifecycle offerLifecycle) {
        this.offerLifecycle = offerLifecycle;
        this.consumer = new EventConsumer(codec, "dispatch-trip-events")
                .on(EventTypes.TRIP_REQUESTED, TripRequestedPayload.class,
                        event -> matchingService.onTripRequested(event.payload(), event.eventId()))
                .on(EventTypes.TRIP_MATCHING_RESTARTED, TripRequestedPayload.class,
                        event -> matchingService.onTripRequested(event.payload(), event.eventId()))
                .on(EventTypes.TRIP_COMPLETED, event -> closeForTrip(event.aggregateId()))
                .on(EventTypes.TRIP_CANCELLED, event -> closeForTrip(event.aggregateId()))
                .on(EventTypes.TRIP_UNMATCHED, event -> closeForTrip(event.aggregateId()))
                .on(EventTypes.TRIP_ACCEPT_REJECTED, OfferLifecyclePayload.class, event ->
                        offerLifecycle.releaseAccepted(
                                UUID.fromString(event.payload().offerId()),
                                event.payload().driverId()))
                .on(EventTypes.TRIP_DRIVER_CANCELLED, TripDriverCancelledPayload.class, event ->
                        offerLifecycle.releaseDriver(
                                UUID.fromString(event.payload().tripId()),
                                event.payload().driverId()));
    }

    @KafkaListener(topics = Topics.TRIP_EVENTS, groupId = "dispatch-trip-events")
    public void onTripEvent(String message) {
        consumer.consume(message);
    }

    private void closeForTrip(String tripId) {
        offerLifecycle.closeForTrip(UUID.fromString(tripId));
    }
}

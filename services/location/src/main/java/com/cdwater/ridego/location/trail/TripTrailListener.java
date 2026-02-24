package com.cdwater.ridego.location.trail;

import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.contracts.TripCompletedPayload;
import com.cdwater.ridego.contracts.TripDriverCancelledPayload;
import com.cdwater.ridego.contracts.TripDriverPayload;
import com.cdwater.ridego.location.geo.ActiveTripRegistry;
import com.cdwater.ridego.messaging.EventConsumer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TripTrailListener {

    private final ActiveTripRegistry activeTrips;
    private final EventConsumer consumer;

    public TripTrailListener(EventEnvelopeCodec codec,
                             ActiveTripRegistry activeTrips) {
        this.activeTrips = activeTrips;
        this.consumer = new EventConsumer(codec, "location-trip-events")
                .on(EventTypes.TRIP_STARTED, TripDriverPayload.class,
                        event -> activeTrips.start(
                                event.payload().tripId(), event.payload().driverId()))
                .on(EventTypes.TRIP_COMPLETED, TripCompletedPayload.class,
                        event -> activeTrips.endByDriver(event.payload().driverId()))
                .on(EventTypes.TRIP_DRIVER_CANCELLED, TripDriverCancelledPayload.class,
                        event -> activeTrips.endByTrip(event.payload().tripId()))
                .on(EventTypes.TRIP_CANCELLED, event -> activeTrips.endByTrip(event.aggregateId()));
    }

    @KafkaListener(topics = Topics.TRIP_EVENTS, groupId = "location-trip-events")
    public void onTripEvent(String message) {
        consumer.consume(message);
    }
}

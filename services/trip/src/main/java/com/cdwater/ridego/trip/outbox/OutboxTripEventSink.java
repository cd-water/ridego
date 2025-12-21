package com.cdwater.ridego.trip.outbox;

import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.messaging.OutboxRepository;
import com.cdwater.ridego.trip.lifecycle.TripEvent;
import com.cdwater.ridego.trip.lifecycle.TripEventSink;

public class OutboxTripEventSink implements TripEventSink {

    private final OutboxRepository outbox;

    public OutboxTripEventSink(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    public void append(TripEvent event) {
        outbox.append(Topics.TRIP_EVENTS, event.tripId().toString(), event.cityId(),
                event.type(), event.payload());
    }
}

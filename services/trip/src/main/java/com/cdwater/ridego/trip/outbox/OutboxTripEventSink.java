package com.cdwater.ridego.trip.outbox;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.trip.lifecycle.TripEvent;
import com.cdwater.ridego.trip.lifecycle.TripEventSink;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

public class OutboxTripEventSink implements TripEventSink {

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public OutboxTripEventSink(OutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(TripEvent event) {
        EventEnvelope<Object> envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                event.type(),
                1,
                event.occurredAt(),
                event.tripId().toString(),
                event.cityId(),
                event.tripId().toString(),
                null,
                event.payload());
        outbox.append(Topics.TRIP_EVENTS, event.tripId().toString(), envelope);
    }
}

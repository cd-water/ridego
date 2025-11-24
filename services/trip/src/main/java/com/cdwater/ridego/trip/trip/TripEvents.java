package com.cdwater.ridego.trip.trip;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.trip.outbox.OutboxRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class TripEvents {

    private final OutboxRepository outbox;

    public TripEvents(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    public void append(String eventType, TripRecord trip, Object payload) {
        EventEnvelope<Object> event = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                1,
                Instant.now(),
                trip.id().toString(),
                trip.cityId(),
                trip.id().toString(),
                null,
                payload);
        outbox.append(Topics.TRIP_EVENTS, trip.id().toString(), event);
    }
}

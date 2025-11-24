package com.cdwater.ridego.dispatch.outbox;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.Topics;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class DispatchEvents {

    private final OutboxRepository outbox;

    public DispatchEvents(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    public void append(String eventType, UUID tripId, String cityId, Object payload) {
        EventEnvelope<Object> event = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                1,
                Instant.now(),
                tripId.toString(),
                cityId,
                tripId.toString(),
                null,
                payload);
        outbox.append(Topics.DISPATCH_EVENTS, tripId.toString(), event);
    }
}

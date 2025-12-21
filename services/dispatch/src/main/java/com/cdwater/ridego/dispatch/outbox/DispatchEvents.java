package com.cdwater.ridego.dispatch.outbox;

import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.messaging.OutboxRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DispatchEvents {

    private final OutboxRepository outbox;

    public DispatchEvents(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    public void append(String eventType, UUID tripId, String cityId, Object payload) {
        outbox.append(Topics.DISPATCH_EVENTS, tripId.toString(), cityId, eventType, payload);
    }
}

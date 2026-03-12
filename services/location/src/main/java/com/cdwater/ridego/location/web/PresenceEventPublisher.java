package com.cdwater.ridego.location.web;

import com.cdwater.ridego.contracts.DriverPresenceChangedPayload;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.location.presence.PresenceEventSink;
import com.cdwater.ridego.messaging.OutboxRepository;
import org.springframework.stereotype.Component;

@Component
public class PresenceEventPublisher implements PresenceEventSink {

    private final OutboxRepository outbox;

    public PresenceEventPublisher(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    public void publish(String driverId, String cityId, boolean online) {
        outbox.append(Topics.LOCATION_EVENTS, driverId, cityId,
                EventTypes.DRIVER_PRESENCE_CHANGED,
                new DriverPresenceChangedPayload(driverId, cityId, online));
    }
}

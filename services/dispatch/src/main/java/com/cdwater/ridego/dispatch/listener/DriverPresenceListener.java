package com.cdwater.ridego.dispatch.listener;

import com.cdwater.ridego.contracts.DriverPresenceChangedPayload;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.dispatch.lifecycle.DriverAvailability;
import com.cdwater.ridego.messaging.EventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DriverPresenceListener {

    private static final Logger log = LoggerFactory.getLogger(DriverPresenceListener.class);

    private final DriverAvailability availability;
    private final EventConsumer consumer;

    public DriverPresenceListener(EventEnvelopeCodec codec,
                                  DriverAvailability availability) {
        this.availability = availability;
        this.consumer = new EventConsumer(codec, "dispatch-presence")
                .on(EventTypes.DRIVER_PRESENCE_CHANGED, DriverPresenceChangedPayload.class,
                        event -> apply(event.payload()));
    }

    @KafkaListener(topics = Topics.LOCATION_EVENTS, groupId = "dispatch-presence")
    public void onPresence(String message) {
        consumer.consume(message);
    }

    private void apply(DriverPresenceChangedPayload payload) {
        boolean applied = payload.online()
                ? availability.markOnline(payload.driverId(), payload.cityId())
                : availability.markOffline(payload.driverId(), payload.cityId());
        if (!applied) {
            log.warn("presence update lost for driver={} online={}",
                    payload.driverId(), payload.online());
        }
    }
}

package com.cdwater.ridego.dispatch.listener;

import com.cdwater.ridego.contracts.DriverPresenceChangedPayload;
import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.dispatch.lifecycle.DriverAvailability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DriverPresenceListener {

    private static final Logger log = LoggerFactory.getLogger(DriverPresenceListener.class);

    private final EventEnvelopeCodec codec;
    private final ObjectMapper objectMapper;
    private final DriverAvailability availability;

    public DriverPresenceListener(EventEnvelopeCodec codec,
                                  ObjectMapper objectMapper,
                                  DriverAvailability availability) {
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.availability = availability;
    }

    @KafkaListener(topics = Topics.LOCATION_EVENTS, groupId = "dispatch-presence")
    public void onPresence(String message) {
        EventEnvelope<JsonNode> event = codec.decode(message);
        if (!EventTypes.DRIVER_PRESENCE_CHANGED.equals(event.eventType())) {
            return;
        }
        DriverPresenceChangedPayload payload = objectMapper.treeToValue(
                event.payload(), DriverPresenceChangedPayload.class);

        boolean applied = payload.online()
                ? availability.markOnline(payload.driverId(), payload.cityId())
                : availability.markOffline(payload.driverId(), payload.cityId());
        if (!applied) {
            log.warn("presence update lost for driver={} online={}",
                    payload.driverId(), payload.online());
        }
    }
}

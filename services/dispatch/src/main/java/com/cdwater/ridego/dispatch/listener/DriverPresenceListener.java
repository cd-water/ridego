package com.cdwater.ridego.dispatch.listener;

import com.cdwater.ridego.contracts.DriverPresenceChangedPayload;
import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.dispatch.availability.AvailabilityRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DriverPresenceListener {

    private final EventEnvelopeCodec codec;
    private final ObjectMapper objectMapper;
    private final AvailabilityRepository availability;

    public DriverPresenceListener(EventEnvelopeCodec codec,
                                  ObjectMapper objectMapper,
                                  AvailabilityRepository availability) {
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

        if (payload.online()) {
            availability.online(payload.driverId(), payload.cityId());
        } else {
            availability.offline(payload.driverId(), payload.cityId());
        }
    }
}

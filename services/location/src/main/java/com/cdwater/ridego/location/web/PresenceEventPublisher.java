package com.cdwater.ridego.location.web;

import com.cdwater.ridego.contracts.DriverPresenceChangedPayload;
import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.Topics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Component
public class PresenceEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PresenceEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(String driverId, String cityId, boolean online) {
        EventEnvelope<DriverPresenceChangedPayload> event = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                EventTypes.DRIVER_PRESENCE_CHANGED,
                1,
                Instant.now(),
                driverId,
                cityId,
                null,
                null,
                new DriverPresenceChangedPayload(driverId, cityId, online));
        kafkaTemplate.send(Topics.LOCATION_EVENTS, driverId, objectMapper.writeValueAsString(event));
    }
}

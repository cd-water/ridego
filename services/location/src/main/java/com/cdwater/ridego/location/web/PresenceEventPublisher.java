package com.cdwater.ridego.location.web;

import com.cdwater.ridego.contracts.DriverPresenceChangedPayload;
import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.location.presence.PresenceEventSink;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PresenceEventPublisher implements PresenceEventSink {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventEnvelopeCodec codec;

    public PresenceEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                  EventEnvelopeCodec codec) {
        this.kafkaTemplate = kafkaTemplate;
        this.codec = codec;
    }

    public void publish(String driverId, String cityId, boolean online) {
        EventEnvelope<Object> event = codec.envelope(
                EventTypes.DRIVER_PRESENCE_CHANGED, driverId, cityId, null, null,
                new DriverPresenceChangedPayload(driverId, cityId, online));
        kafkaTemplate.send(Topics.LOCATION_EVENTS, driverId, codec.encode(event));
    }
}

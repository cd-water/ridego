package com.cdwater.ridego.contracts;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.UUID;

public class EventEnvelopeCodec {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EventEnvelopeCodec(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public EventEnvelope<JsonNode> decode(String message) {
        return objectMapper.readValue(message, new TypeReference<EventEnvelope<JsonNode>>() {
        });
    }

    public <P> EventEnvelope<P> decode(String message, Class<P> payloadType) {
        return bind(decode(message), payloadType);
    }

    public <P> EventEnvelope<P> bind(EventEnvelope<JsonNode> raw, Class<P> payloadType) {
        P payload = objectMapper.treeToValue(raw.payload(), payloadType);
        return new EventEnvelope<>(
                raw.eventId(), raw.eventType(), raw.eventVersion(), raw.occurredAt(),
                raw.aggregateId(), raw.cityId(), raw.correlationId(), raw.causationId(),
                payload);
    }

    public EventEnvelope<Object> envelope(String eventType,
                                          String aggregateId,
                                          String cityId,
                                          String correlationId,
                                          String causationId,
                                          Object payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                1,
                clock.instant(),
                aggregateId,
                cityId,
                correlationId,
                causationId,
                payload);
    }

    public String encode(EventEnvelope<?> envelope) {
        return objectMapper.writeValueAsString(envelope);
    }
}

package com.cdwater.ridego.messaging;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Decodes an event envelope once, routes it to the handler registered for its type,
 * and optionally skips redeliveries. Consumers are expected to run inside the
 * caller's transaction when dedup is enabled, so the dedup row commits with the work.
 */
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final EventEnvelopeCodec codec;
    private final String name;
    private final Map<String, Consumer<EventEnvelope<JsonNode>>> handlers = new HashMap<>();
    private Consumer<EventEnvelope<JsonNode>> fallback;
    private EventDedup dedup;

    public EventConsumer(EventEnvelopeCodec codec, String name) {
        this.codec = codec;
        this.name = name;
    }

    public <P> EventConsumer on(String eventType,
                                Class<P> payloadType,
                                Consumer<EventEnvelope<P>> handler) {
        handlers.put(eventType, event -> handler.accept(codec.bind(event, payloadType)));
        return this;
    }

    public EventConsumer on(String eventType, Consumer<EventEnvelope<JsonNode>> handler) {
        handlers.put(eventType, handler);
        return this;
    }

    /**
     * Register a catch-all handler for consumers that project every event type.
     */
    public EventConsumer onAny(Consumer<EventEnvelope<JsonNode>> handler) {
        this.fallback = handler;
        return this;
    }

    public EventConsumer dedup(EventDedup dedup) {
        this.dedup = dedup;
        return this;
    }

    public void consume(String message) {
        EventEnvelope<JsonNode> event = codec.decode(message);
        Consumer<EventEnvelope<JsonNode>> handler =
                handlers.getOrDefault(event.eventType(), fallback);
        if (handler == null) {
            log.debug("[{}] ignoring event type={} id={}", name, event.eventType(), event.eventId());
            return;
        }
        if (dedup != null && !dedup.markOnce(name, event.eventId())) {
            log.debug("[{}] skipping duplicate event id={}", name, event.eventId());
            return;
        }
        handler.accept(event);
    }
}

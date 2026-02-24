package com.cdwater.ridego.messaging;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.TripRequestedPayload;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EventConsumerTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private final EventEnvelopeCodec codec =
            new EventEnvelopeCodec(new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void bindsTypedPayloadForRegisteredType() {
        List<EventEnvelope<TripRequestedPayload>> seen = new ArrayList<>();

        new EventConsumer(codec, "test")
                .on(EventTypes.TRIP_REQUESTED, TripRequestedPayload.class, seen::add)
                .consume(tripRequested("trip-1"));

        assertEquals(1, seen.size());
        EventEnvelope<TripRequestedPayload> event = seen.getFirst();
        assertEquals("trip-1", event.payload().tripId());
        assertEquals("rider-1", event.payload().riderId());
        assertEquals("trip-1", event.aggregateId());
        assertNotNull(event.eventId());
    }

    @Test
    void routesEachTypeToItsHandler() {
        AtomicInteger requested = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();

        EventConsumer consumer = new EventConsumer(codec, "test")
                .on(EventTypes.TRIP_REQUESTED, TripRequestedPayload.class,
                        event -> requested.incrementAndGet())
                .on(EventTypes.TRIP_CANCELLED, event -> cancelled.incrementAndGet());

        consumer.consume(tripRequested("trip-1"));
        consumer.consume(envelope(EventTypes.TRIP_CANCELLED, "trip-2", Map.of()));

        assertEquals(1, requested.get());
        assertEquals(1, cancelled.get());
    }

    @Test
    void ignoresUnregisteredType() {
        AtomicInteger requested = new AtomicInteger();

        EventConsumer consumer = new EventConsumer(codec, "test")
                .on(EventTypes.TRIP_REQUESTED, TripRequestedPayload.class,
                        event -> requested.incrementAndGet());

        assertDoesNotThrow(() ->
                consumer.consume(envelope(EventTypes.TRIP_COMPLETED, "trip-1", Map.of())));
        assertEquals(0, requested.get());
    }

    @Test
    void dedupSkipsRedeliveredEvent() {
        AtomicInteger calls = new AtomicInteger();
        Set<String> seen = new HashSet<>();
        List<String> consumers = new ArrayList<>();
        EventDedup dedup = (consumer, eventId) -> {
            consumers.add(consumer);
            return seen.add(eventId);
        };
        EventConsumer consumer = new EventConsumer(codec, "trip-projection")
                .dedup(dedup)
                .on(EventTypes.TRIP_CANCELLED, event -> calls.incrementAndGet());
        String message = envelope(EventTypes.TRIP_CANCELLED, "trip-1", Map.of());

        consumer.consume(message);
        consumer.consume(message);

        assertEquals(1, calls.get());
        assertEquals(List.of("trip-projection", "trip-projection"), consumers);
        assertEquals(1, seen.size());
    }

    @Test
    void onAnyHandlesEveryEventType() {
        AtomicInteger calls = new AtomicInteger();

        EventConsumer consumer = new EventConsumer(codec, "test")
                .onAny(event -> calls.incrementAndGet());

        consumer.consume(tripRequested("trip-1"));
        consumer.consume(envelope(EventTypes.TRIP_COMPLETED, "trip-1", Map.of()));

        assertEquals(2, calls.get());
    }

    @Test
    void typedHandlerWinsOverCatchAll() {
        AtomicInteger typed = new AtomicInteger();
        AtomicInteger any = new AtomicInteger();

        EventConsumer consumer = new EventConsumer(codec, "test")
                .on(EventTypes.TRIP_REQUESTED, TripRequestedPayload.class,
                        event -> typed.incrementAndGet())
                .onAny(event -> any.incrementAndGet());

        consumer.consume(tripRequested("trip-1"));
        consumer.consume(envelope(EventTypes.TRIP_COMPLETED, "trip-1", Map.of()));

        assertEquals(1, typed.get());
        assertEquals(1, any.get());
    }

    private String tripRequested(String tripId) {
        return envelope(EventTypes.TRIP_REQUESTED, tripId,
                new TripRequestedPayload(tripId, "rider-1", "city-1",
                        31.2, 121.4, 31.3, 121.5));
    }

    private String envelope(String eventType, String aggregateId, Object payload) {
        return codec.encode(
                codec.envelope(eventType, aggregateId, "city-1", aggregateId, null, payload));
    }
}

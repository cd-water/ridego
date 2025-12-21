package com.cdwater.ridego.contracts;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EventEnvelopeCodecTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private final EventEnvelopeCodec codec = new EventEnvelopeCodec(
            new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void roundTripsTypedPayload() {
        EventEnvelope<Object> envelope = codec.envelope(
                EventTypes.TRIP_REQUESTED, "trip-1", "city-1", "trip-1", null,
                new TripRequestedPayload("trip-1", "rider-1", "city-1",
                        31.2304, 121.4737, 31.24, 121.49));

        EventEnvelope<TripRequestedPayload> decoded = codec.decode(
                codec.encode(envelope), TripRequestedPayload.class);

        assertNotNull(decoded.eventId());
        assertEquals(EventTypes.TRIP_REQUESTED, decoded.eventType());
        assertEquals(NOW, decoded.occurredAt());
        assertEquals("trip-1", decoded.aggregateId());
        assertEquals("rider-1", decoded.payload().riderId());
        assertEquals(31.2304, decoded.payload().pickupLat());
    }
}

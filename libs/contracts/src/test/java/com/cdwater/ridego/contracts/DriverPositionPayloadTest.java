package com.cdwater.ridego.contracts;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DriverPositionPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roundTripsWithEventTime() {
        Instant ts = Instant.parse("2026-11-13T10:00:00Z");
        DriverPositionPayload payload = new DriverPositionPayload(
                "driver-1", "city-1", 31.2304, 121.4737, ts);

        DriverPositionPayload decoded = objectMapper.readValue(
                objectMapper.writeValueAsString(payload), DriverPositionPayload.class);

        assertEquals(payload, decoded);
        assertEquals(ts, decoded.ts());
    }
}

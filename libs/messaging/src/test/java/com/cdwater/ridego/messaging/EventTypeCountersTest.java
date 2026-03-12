package com.cdwater.ridego.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventTypeCountersTest {

    @Test
    void incrementsOnlyMappedTypes() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EventTypeCounters counters = new EventTypeCounters(registry, Map.of(
                "A", "ridego.test.a",
                "B", "ridego.test.b"));

        counters.increment("A");
        counters.increment("A");
        counters.increment("B");
        counters.increment("C");

        assertEquals(2.0, registry.get("ridego.test.a").counter().count());
        assertEquals(1.0, registry.get("ridego.test.b").counter().count());
        assertNull(registry.find("ridego.test.c").counter());
    }
}

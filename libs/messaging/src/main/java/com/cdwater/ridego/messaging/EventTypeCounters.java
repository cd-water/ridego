package com.cdwater.ridego.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * Counts events by type; types without a mapping are ignored.
 */
public class EventTypeCounters {

    private final Map<String, Counter> counters;

    public EventTypeCounters(MeterRegistry meterRegistry,
                             Map<String, String> metricNamesByEventType) {
        Map<String, Counter> built = new HashMap<>();
        metricNamesByEventType.forEach((eventType, metricName) ->
                built.put(eventType, Counter.builder(metricName).register(meterRegistry)));
        this.counters = Map.copyOf(built);
    }

    public void increment(String eventType) {
        Counter counter = counters.get(eventType);
        if (counter != null) {
            counter.increment();
        }
    }
}

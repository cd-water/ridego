package com.cdwater.ridego.trip.outbox;

import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.trip.lifecycle.TripEvent;
import com.cdwater.ridego.trip.lifecycle.TripEventSink;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;

public class MetricsTripEventSink implements TripEventSink {

    private final TripEventSink delegate;
    private final Map<String, Counter> counters;

    public MetricsTripEventSink(TripEventSink delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.counters = Map.of(
                EventTypes.TRIP_REQUESTED, Counter.builder("ridego.trips.requested").register(meterRegistry),
                EventTypes.TRIP_COMPLETED, Counter.builder("ridego.trips.completed").register(meterRegistry),
                EventTypes.TRIP_CANCELLED, Counter.builder("ridego.trips.cancelled").register(meterRegistry),
                EventTypes.TRIP_UNMATCHED, Counter.builder("ridego.trips.unmatched").register(meterRegistry));
    }

    @Override
    public void append(TripEvent event) {
        delegate.append(event);
        Counter counter = counters.get(event.type());
        if (counter != null) {
            counter.increment();
        }
    }
}

package com.cdwater.ridego.trip.outbox;

import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.messaging.EventTypeCounters;
import com.cdwater.ridego.trip.lifecycle.TripEvent;
import com.cdwater.ridego.trip.lifecycle.TripEventSink;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;

public class MetricsTripEventSink implements TripEventSink {

    private final TripEventSink delegate;
    private final EventTypeCounters counters;

    public MetricsTripEventSink(TripEventSink delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.counters = new EventTypeCounters(meterRegistry, Map.of(
                EventTypes.TRIP_REQUESTED, "ridego.trips.requested",
                EventTypes.TRIP_COMPLETED, "ridego.trips.completed",
                EventTypes.TRIP_CANCELLED, "ridego.trips.cancelled",
                EventTypes.TRIP_UNMATCHED, "ridego.trips.unmatched"));
    }

    @Override
    public void append(TripEvent event) {
        delegate.append(event);
        counters.increment(event.type());
    }
}

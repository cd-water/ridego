package com.cdwater.ridego.dispatch.outbox;

import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.dispatch.lifecycle.DispatchEventSink;
import com.cdwater.ridego.messaging.EventTypeCounters;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;
import java.util.UUID;

public class MetricsDispatchEventSink implements DispatchEventSink {

    private final DispatchEventSink delegate;
    private final EventTypeCounters counters;

    public MetricsDispatchEventSink(DispatchEventSink delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.counters = new EventTypeCounters(meterRegistry, Map.of(
                EventTypes.OFFER_CREATED, "ridego.offers.issued",
                EventTypes.OFFER_ACCEPTED, "ridego.offers.accepted",
                EventTypes.OFFER_REJECTED, "ridego.offers.rejected",
                EventTypes.OFFER_EXPIRED, "ridego.offers.expired"));
    }

    @Override
    public void append(String eventType, UUID tripId, String cityId, Object payload) {
        delegate.append(eventType, tripId, cityId, payload);
        counters.increment(eventType);
    }
}

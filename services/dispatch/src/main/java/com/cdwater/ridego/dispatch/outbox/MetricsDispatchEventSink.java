package com.cdwater.ridego.dispatch.outbox;

import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.dispatch.lifecycle.DispatchEventSink;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;
import java.util.UUID;

public class MetricsDispatchEventSink implements DispatchEventSink {

    private final DispatchEventSink delegate;
    private final Map<String, Counter> counters;

    public MetricsDispatchEventSink(DispatchEventSink delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.counters = Map.of(
                EventTypes.OFFER_CREATED, Counter.builder("ridego.offers.issued").register(meterRegistry),
                EventTypes.OFFER_ACCEPTED, Counter.builder("ridego.offers.accepted").register(meterRegistry),
                EventTypes.OFFER_REJECTED, Counter.builder("ridego.offers.rejected").register(meterRegistry),
                EventTypes.OFFER_EXPIRED, Counter.builder("ridego.offers.expired").register(meterRegistry));
    }

    @Override
    public void append(String eventType, UUID tripId, String cityId, Object payload) {
        delegate.append(eventType, tripId, cityId, payload);
        Counter counter = counters.get(eventType);
        if (counter != null) {
            counter.increment();
        }
    }
}

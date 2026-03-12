package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.messaging.EventTypeCounters;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;
import java.util.UUID;

public class MetricsInvoiceEventSink implements InvoiceEventSink {

    private final InvoiceEventSink delegate;
    private final EventTypeCounters counters;

    public MetricsInvoiceEventSink(InvoiceEventSink delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.counters = new EventTypeCounters(meterRegistry, Map.of(
                EventTypes.INVOICE_PAID, "ridego.invoices.paid"));
    }

    @Override
    public void append(String eventType, UUID tripId, Object payload) {
        delegate.append(eventType, tripId, payload);
        counters.increment(eventType);
    }
}

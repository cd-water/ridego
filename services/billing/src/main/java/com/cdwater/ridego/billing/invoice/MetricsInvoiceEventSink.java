package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.contracts.EventTypes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.UUID;

public class MetricsInvoiceEventSink implements InvoiceEventSink {

    private final InvoiceEventSink delegate;
    private final Counter invoicesPaid;

    public MetricsInvoiceEventSink(InvoiceEventSink delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.invoicesPaid = Counter.builder("ridego.invoices.paid").register(meterRegistry);
    }

    @Override
    public void append(String eventType, UUID tripId, Object payload) {
        delegate.append(eventType, tripId, payload);
        if (EventTypes.INVOICE_PAID.equals(eventType)) {
            invoicesPaid.increment();
        }
    }
}

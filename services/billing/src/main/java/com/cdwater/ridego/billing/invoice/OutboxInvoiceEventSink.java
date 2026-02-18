package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.messaging.OutboxRepository;

import java.util.UUID;

public class OutboxInvoiceEventSink implements InvoiceEventSink {

    private final OutboxRepository outbox;

    public OutboxInvoiceEventSink(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    public void append(String eventType, UUID tripId, Object payload) {
        outbox.append(Topics.BILLING_EVENTS, tripId.toString(), null, eventType, payload);
    }
}

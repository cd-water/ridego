package com.cdwater.ridego.billing.invoice;

import java.util.UUID;

public interface InvoiceEventSink {

    void append(String eventType, UUID tripId, Object payload);
}

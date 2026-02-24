package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.contracts.TripCompletedPayload;
import com.cdwater.ridego.messaging.EventConsumer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TripCompletedListener {

    private final InvoiceModule invoiceModule;
    private final EventConsumer consumer;

    public TripCompletedListener(EventEnvelopeCodec codec, InvoiceModule invoiceModule) {
        this.invoiceModule = invoiceModule;
        this.consumer = new EventConsumer(codec, "billing")
                .on(EventTypes.TRIP_COMPLETED, TripCompletedPayload.class, event -> {
                    Invoice invoice = invoiceModule.issue(
                            event.payload(), event.eventId());
                    invoiceModule.pay(invoice.getId());
                });
    }

    @KafkaListener(topics = Topics.TRIP_EVENTS, groupId = "billing")
    public void onTripEvent(String message) {
        consumer.consume(message);
    }
}

package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.contracts.TripCompletedPayload;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TripCompletedListener {

    private final EventEnvelopeCodec codec;
    private final ObjectMapper objectMapper;
    private final InvoiceModule invoiceModule;

    public TripCompletedListener(EventEnvelopeCodec codec,
                                 ObjectMapper objectMapper,
                                 InvoiceModule invoiceModule) {
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.invoiceModule = invoiceModule;
    }

    @KafkaListener(topics = Topics.TRIP_EVENTS, groupId = "billing")
    public void onTripEvent(String message) {
        EventEnvelope<JsonNode> event = codec.decode(message);
        if (!EventTypes.TRIP_COMPLETED.equals(event.eventType())) {
            return;
        }
        TripCompletedPayload payload = objectMapper.treeToValue(
                event.payload(), TripCompletedPayload.class);
        Invoice invoice = invoiceModule.issue(payload, event.eventId());
        invoiceModule.pay(invoice.getId());
    }
}

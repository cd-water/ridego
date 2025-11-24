package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.contracts.TripCompletedPayload;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TripCompletedListener {

    private final ObjectMapper objectMapper;
    private final InvoiceService invoiceService;

    public TripCompletedListener(ObjectMapper objectMapper, InvoiceService invoiceService) {
        this.objectMapper = objectMapper;
        this.invoiceService = invoiceService;
    }

    @KafkaListener(topics = Topics.TRIP_EVENTS, groupId = "billing")
    public void onTripEvent(String message) {
        EventEnvelope<JsonNode> event = objectMapper.readValue(
                message, new TypeReference<EventEnvelope<JsonNode>>() {
                });
        if (!EventTypes.TRIP_COMPLETED.equals(event.eventType())) {
            return;
        }
        TripCompletedPayload payload = objectMapper.treeToValue(
                event.payload(), TripCompletedPayload.class);
        invoiceService.onTripCompleted(payload, event.eventId());
    }
}

package com.cdwater.ridego.trip.projection;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.messaging.ProcessedEvents;
import com.cdwater.ridego.trip.read.TripReadModel;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Component
public class TripProjectionListener {

    private static final String CONSUMER = "trip-projection";

    private final EventEnvelopeCodec codec;
    private final ProcessedEvents processedEvents;
    private final TripReadModel readModel;

    public TripProjectionListener(EventEnvelopeCodec codec,
                                  ProcessedEvents processedEvents,
                                  TripReadModel readModel) {
        this.codec = codec;
        this.processedEvents = processedEvents;
        this.readModel = readModel;
    }

    @KafkaListener(topics = Topics.TRIP_EVENTS, groupId = CONSUMER)
    @Transactional
    public void onMessage(String message) {
        EventEnvelope<JsonNode> event = codec.decode(message);
        if (!processedEvents.markOnce(CONSUMER, event.eventId())) {
            return;
        }
        readModel.apply(event);
    }
}

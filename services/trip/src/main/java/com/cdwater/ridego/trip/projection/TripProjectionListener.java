package com.cdwater.ridego.trip.projection;

import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.messaging.EventConsumer;
import com.cdwater.ridego.messaging.ProcessedEvents;
import com.cdwater.ridego.trip.read.TripReadModel;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TripProjectionListener {

    private static final String CONSUMER = "trip-projection";

    private final EventConsumer consumer;

    public TripProjectionListener(EventEnvelopeCodec codec,
                                  ProcessedEvents processedEvents,
                                  TripReadModel readModel) {
        this.consumer = new EventConsumer(codec, CONSUMER)
                .dedup(processedEvents)
                .onAny(readModel::apply);
    }

    @KafkaListener(topics = Topics.TRIP_EVENTS, groupId = CONSUMER)
    @Transactional
    public void onMessage(String message) {
        consumer.consume(message);
    }
}

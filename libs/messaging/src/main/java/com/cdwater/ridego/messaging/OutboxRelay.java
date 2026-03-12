package com.cdwater.ridego.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int maxAttempts;

    public OutboxRelay(OutboxRepository outbox,
                       KafkaTemplate<String, String> kafkaTemplate,
                       int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${ridego.outbox.poll-interval-ms:500}")
    public void relay() {
        for (OutboxRepository.OutboxRow row : outbox.findUnpublished(100)) {
            try {
                kafkaTemplate.send(row.topic(), row.key(), row.payload())
                        .get(5, TimeUnit.SECONDS);
                outbox.markPublished(row.id());
            } catch (Exception e) {
                int attempts = outbox.markFailed(row.id());
                if (attempts >= maxAttempts) {
                    outbox.markParked(row.id());
                    log.error("outbox row id={} topic={} parked after {} attempts",
                            row.id(), row.topic(), attempts, e);
                } else {
                    log.warn("outbox publish failed for id={} attempt={}, will retry",
                            row.id(), attempts, e);
                    return;
                }
            }
        }
    }
}

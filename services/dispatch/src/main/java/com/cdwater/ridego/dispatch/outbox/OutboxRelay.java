package com.cdwater.ridego.dispatch.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * ponytail: 与 trip/billing 的 outbox 中继重复；出现第四个使用者时抽到 libs。
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafkaTemplate) {
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${ridego.outbox.poll-interval-ms:500}")
    public void relay() {
        for (OutboxRepository.OutboxRow row : outbox.findUnpublished(100)) {
            try {
                kafkaTemplate.send(row.topic(), row.key(), row.payload())
                        .get(5, TimeUnit.SECONDS);
                outbox.markPublished(row.id());
            } catch (Exception e) {
                outbox.markFailed(row.id());
                log.warn("outbox publish failed for id={}, will retry", row.id(), e);
                return;
            }
        }
    }
}

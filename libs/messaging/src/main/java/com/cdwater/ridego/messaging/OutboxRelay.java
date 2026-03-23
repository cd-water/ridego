package com.cdwater.ridego.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publishes outbox rows in id order, preserving ordering per event key.
 * Different keys are relayed in parallel so a burst of unrelated events
 * (for example, a fleet coming online) is not serialized behind one worker.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int maxAttempts;
    private final int batchSize;
    private final ExecutorService workers;

    public OutboxRelay(OutboxRepository outbox,
                       KafkaTemplate<String, String> kafkaTemplate,
                       int maxAttempts,
                       int batchSize,
                       int workers) {
        if (maxAttempts < 1 || batchSize < 1 || workers < 1) {
            throw new IllegalArgumentException("relay limits must be positive");
        }
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
        this.workers = Executors.newFixedThreadPool(workers, runnable -> {
            Thread thread = new Thread(runnable, "outbox-relay");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Scheduled(fixedDelayString = "${ridego.outbox.poll-interval-ms:500}")
    public void relay() {
        List<OutboxRepository.OutboxRow> rows = outbox.findUnpublished(batchSize);
        if (rows.isEmpty()) {
            return;
        }

        Map<String, List<OutboxRepository.OutboxRow>> byKey = new LinkedHashMap<>();
        for (OutboxRepository.OutboxRow row : rows) {
            byKey.computeIfAbsent(row.key(), key -> new ArrayList<>()).add(row);
        }

        AtomicBoolean stop = new AtomicBoolean(false);
        List<Future<?>> tasks = new ArrayList<>(byKey.size());
        for (List<OutboxRepository.OutboxRow> group : byKey.values()) {
            tasks.add(workers.submit(() -> relayGroup(group, stop)));
        }
        for (Future<?> task : tasks) {
            try {
                task.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException e) {
                log.error("outbox relay worker failed", e.getCause());
            }
        }
    }

    private void relayGroup(List<OutboxRepository.OutboxRow> group, AtomicBoolean stop) {
        for (OutboxRepository.OutboxRow row : group) {
            if (stop.get()) {
                return;
            }
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
                    stop.set(true);
                    log.warn("outbox publish failed for id={} attempt={}, will retry",
                            row.id(), attempts, e);
                    return;
                }
            }
        }
    }
}

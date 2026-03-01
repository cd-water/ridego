package com.cdwater.ridego.simulator.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RunMetrics {

    private final Map<String, Instant> requestedAt = new ConcurrentHashMap<>();
    private final Map<String, Instant> acceptSentAt = new ConcurrentHashMap<>();
    private final Set<String> matchedTrips = ConcurrentHashMap.newKeySet();
    private final List<Long> matchLatenciesMs = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> acceptPropagationMs = Collections.synchronizedList(new ArrayList<>());

    private final AtomicLong tripsRequested = new AtomicLong();
    private final AtomicLong offers = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong unmatched = new AtomicLong();
    private final AtomicLong cancelled = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong acceptFailures = new AtomicLong();
    private final AtomicLong positionsSent = new AtomicLong();

    public void tripRequested(String tripId, Instant at) {
        tripsRequested.incrementAndGet();
        requestedAt.put(tripId, at);
    }

    public void offerCreated(String tripId, Instant at) {
        offers.incrementAndGet();
        matchedTrips.add(tripId);
        Instant requested = requestedAt.get(tripId);
        if (requested != null) {
            matchLatenciesMs.add(Math.max(0, Duration.between(requested, at).toMillis()));
        }
    }

    public void acceptSent(String tripId, Instant at) {
        acceptSentAt.put(tripId, at);
    }

    public void tripAccepted(String tripId, Instant at) {
        Instant sent = acceptSentAt.remove(tripId);
        if (sent != null) {
            acceptPropagationMs.add(Math.max(0, Duration.between(sent, at).toMillis()));
        }
    }

    public void acceptFailed(String tripId) {
        acceptFailures.incrementAndGet();
        acceptSentAt.remove(tripId);
    }

    public void tripCompleted(String tripId) {
        completed.incrementAndGet();
        requestedAt.remove(tripId);
    }

    public void tripUnmatched(String tripId) {
        unmatched.incrementAndGet();
        requestedAt.remove(tripId);
    }

    public void tripCancelled(String tripId) {
        cancelled.incrementAndGet();
        requestedAt.remove(tripId);
    }

    public void driverRejected() {
        rejected.incrementAndGet();
    }

    public void positionsSent(long count) {
        positionsSent.addAndGet(count);
    }

    public Snapshot snapshot() {
        long requested = tripsRequested.get();
        long offered = offers.get();
        long finished = completed.get();
        return new Snapshot(
                requested, offered, finished,
                unmatched.get(), cancelled.get(), rejected.get(),
                acceptFailures.get(), positionsSent.get(),
                rate(matchedTrips.size(), requested), rate(finished, requested),
                p95(matchLatenciesMs), p95(acceptPropagationMs));
    }

    static long p95(List<Long> values) {
        List<Long> copy;
        synchronized (values) {
            copy = new ArrayList<>(values);
        }
        if (copy.isEmpty()) {
            return -1;
        }
        Collections.sort(copy);
        int index = (int) Math.min(copy.size() - 1, Math.ceil(copy.size() * 0.95) - 1);
        return copy.get(index);
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    public record Snapshot(long tripsRequested,
                           long offers,
                           long completed,
                           long unmatched,
                           long cancelled,
                           long rejected,
                           long acceptFailures,
                           long positionsSent,
                           double matchRate,
                           double completionRate,
                           long p95MatchLatencyMs,
                           long p95AcceptToEventMs) {
    }
}

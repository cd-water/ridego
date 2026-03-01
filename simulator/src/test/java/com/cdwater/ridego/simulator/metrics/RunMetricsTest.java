package com.cdwater.ridego.simulator.metrics;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunMetricsTest {

    private static final Instant T0 = Instant.parse("2026-11-13T10:00:00Z");

    @Test
    void emptySnapshotHasZeroRatesAndNoP95() {
        RunMetrics.Snapshot snapshot = new RunMetrics().snapshot();

        assertEquals(0, snapshot.tripsRequested());
        assertEquals(0.0, snapshot.matchRate());
        assertEquals(0.0, snapshot.completionRate());
        assertEquals(-1, snapshot.p95MatchLatencyMs());
        assertEquals(-1, snapshot.p95AcceptToEventMs());
    }

    @Test
    void matchAndCompletionRatesUseRequestedAsDenominator() {
        RunMetrics metrics = new RunMetrics();
        metrics.tripRequested("t1", T0);
        metrics.offerCreated("t1", T0.plusMillis(500));
        metrics.tripCompleted("t1");

        RunMetrics.Snapshot snapshot = metrics.snapshot();

        assertEquals(1, snapshot.tripsRequested());
        assertEquals(1, snapshot.offers());
        assertEquals(1, snapshot.completed());
        assertEquals(1.0, snapshot.matchRate());
        assertEquals(1.0, snapshot.completionRate());
        assertEquals(500, snapshot.p95MatchLatencyMs());
    }

    @Test
    void retriedOffersDoNotInflateMatchRate() {
        RunMetrics metrics = new RunMetrics();
        metrics.tripRequested("t1", T0);
        metrics.offerCreated("t1", T0.plusMillis(100));
        metrics.offerCreated("t1", T0.plusMillis(200));

        RunMetrics.Snapshot snapshot = metrics.snapshot();

        assertEquals(2, snapshot.offers());
        assertEquals(1.0, snapshot.matchRate());
    }

    @Test
    void unmatchedReducesCompletionRateOnly() {
        RunMetrics metrics = new RunMetrics();
        metrics.tripRequested("t1", T0);
        metrics.tripRequested("t2", T0);
        metrics.offerCreated("t1", T0.plusMillis(100));
        metrics.tripCompleted("t1");
        metrics.tripUnmatched("t2");

        RunMetrics.Snapshot snapshot = metrics.snapshot();

        assertEquals(0.5, snapshot.matchRate());
        assertEquals(0.5, snapshot.completionRate());
        assertEquals(1, snapshot.unmatched());
    }

    @Test
    void acceptToEventLatencyIsRecordedOnce() {
        RunMetrics metrics = new RunMetrics();
        metrics.acceptSent("t1", T0);
        metrics.tripAccepted("t1", T0.plusMillis(800));
        metrics.tripAccepted("t1", T0.plusMillis(1500));

        assertEquals(800, metrics.snapshot().p95AcceptToEventMs());
    }

    @Test
    void failedAcceptClearsPendingLatency() {
        RunMetrics metrics = new RunMetrics();
        metrics.acceptSent("t1", T0);
        metrics.acceptFailed("t1");
        metrics.tripAccepted("t1", T0.plusMillis(800));

        RunMetrics.Snapshot snapshot = metrics.snapshot();

        assertEquals(1, snapshot.acceptFailures());
        assertEquals(-1, snapshot.p95AcceptToEventMs());
    }

    @Test
    void p95UsesNearestRank() {
        List<Long> hundred = LongStream.rangeClosed(1, 100).boxed().toList();
        List<Long> ten = LongStream.rangeClosed(1, 10).boxed().toList();

        assertEquals(95, RunMetrics.p95(hundred));
        assertEquals(10, RunMetrics.p95(ten));
    }
}

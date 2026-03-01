package com.cdwater.ridego.simulator;

import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.messaging.EventConsumer;
import com.cdwater.ridego.simulator.client.RideGoClient;
import com.cdwater.ridego.simulator.fleet.DriverFleet;
import com.cdwater.ridego.simulator.metrics.RunMetrics;
import com.cdwater.ridego.simulator.scenario.Scenario;
import com.cdwater.ridego.simulator.scenario.SimulatorSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component("load")
@ConditionalOnProperty(prefix = "ridego.simulator", name = "scenario", havingValue = "load")
public class LoadSimulator implements Scenario {

    private static final Logger log = LoggerFactory.getLogger(LoadSimulator.class);

    private static final int SALT_REJECT = 1;
    private static final int SALT_CANCEL = 2;

    private final RideGoClient client;
    private final DriverFleet fleet;
    private final RunMetrics metrics;
    private final SimulatorSettings settings;
    private final Clock clock;
    private final EventConsumer consumer;

    @Value("${ridego.simulator.load.gps-interval-seconds:3}")
    private int gpsIntervalSeconds;
    @Value("${ridego.simulator.load.trip-rate-per-minute:100}")
    private int tripRatePerMinute;
    @Value("${ridego.simulator.load.duration-minutes:2}")
    private double durationMinutes;
    @Value("${ridego.simulator.load.pickup-lat:31.2304}")
    private double pickupLat;
    @Value("${ridego.simulator.load.pickup-lng:121.4737}")
    private double pickupLng;
    @Value("${ridego.simulator.load.dropoff-lat:31.2400}")
    private double dropoffLat;
    @Value("${ridego.simulator.load.dropoff-lng:121.4900}")
    private double dropoffLng;
    @Value("${ridego.simulator.load.in-progress-seconds:5}")
    private int inProgressSeconds;
    @Value("${ridego.simulator.load.driver-reject-rate:0.1}")
    private double driverRejectRate;
    @Value("${ridego.simulator.load.rider-cancel-rate:0.05}")
    private double riderCancelRate;

    private ExecutorService offerExecutor;
    private ExecutorService presenceExecutor;
    private ScheduledExecutorService scheduler;
    private String riderToken;
    private long gpsTick;

    public LoadSimulator(RideGoClient client,
                         DriverFleet fleet,
                         RunMetrics metrics,
                         SimulatorSettings settings,
                         Clock clock,
                         EventEnvelopeCodec codec) {
        this.client = client;
        this.fleet = fleet;
        this.metrics = metrics;
        this.settings = settings;
        this.clock = clock;
        this.consumer = new EventConsumer(codec, "load-simulator")
                .on(EventTypes.TRIP_REQUESTED, event ->
                        metrics.tripRequested(event.aggregateId(), event.occurredAt()))
                .on(EventTypes.OFFER_CREATED, OfferLifecyclePayload.class, event -> {
                    OfferLifecyclePayload payload = event.payload();
                    metrics.offerCreated(payload.tripId(), event.occurredAt());
                    try {
                        offerExecutor.submit(() ->
                                drive(payload.offerId(), payload.tripId(), payload.driverId()));
                    } catch (RejectedExecutionException ignored) {
                        // the run is shutting down; late offers are not driven
                    }
                })
                .on(EventTypes.TRIP_ACCEPTED, event ->
                        metrics.tripAccepted(event.aggregateId(), event.occurredAt()))
                .on(EventTypes.TRIP_COMPLETED, event ->
                        metrics.tripCompleted(event.aggregateId()))
                .on(EventTypes.TRIP_UNMATCHED, event ->
                        metrics.tripUnmatched(event.aggregateId()))
                .on(EventTypes.TRIP_CANCELLED, event ->
                        metrics.tripCancelled(event.aggregateId()));
    }

    @Override
    public void run() throws Exception {
        log.info("load scenario: drivers={} gpsInterval={}s tripRate={}/min duration={}min "
                        + "seed={} driverRejectRate={} riderCancelRate={}",
                fleet.size(), gpsIntervalSeconds, tripRatePerMinute, durationMinutes,
                fleet.seed(), driverRejectRate, riderCancelRate);

        riderToken = client.login(settings.riderUsername, settings.riderPassword).token();

        offerExecutor = Executors.newFixedThreadPool(64);
        presenceExecutor = Executors.newFixedThreadPool(64);
        scheduler = Executors.newScheduledThreadPool(2);
        try {
            bringDriversOnline();
            Thread.sleep(3000);

            scheduler.scheduleAtFixedRate(this::sendPositions, 0, gpsIntervalSeconds, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(this::createTrip, 0,
                    60_000 / Math.max(1, tripRatePerMinute), TimeUnit.MILLISECONDS);

            Thread.sleep((long) (durationMinutes * 60_000));
        } finally {
            scheduler.shutdownNow();
            offerExecutor.shutdown();
            presenceExecutor.shutdown();
            offerExecutor.awaitTermination(15, TimeUnit.SECONDS);
        }
        printMetrics();
    }

    @KafkaListener(topics = {Topics.DISPATCH_EVENTS, Topics.TRIP_EVENTS},
            groupId = "load-simulator-#{T(java.util.UUID).randomUUID()}")
    public void onEvent(String message) {
        consumer.consume(message);
    }

    private void bringDriversOnline() throws Exception {
        List<Future<?>> futures = new ArrayList<>(fleet.size());
        for (String driverId : fleet.driverIds()) {
            futures.add(presenceExecutor.submit(() -> bringOnline(driverId)));
        }
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        log.info("{} drivers online", fleet.size());
    }

    private void bringOnline(String driverId) {
        try {
            client.setPresenceDirect(driverId, settings.cityId);
        } catch (Exception e) {
            log.warn("presence failed for {}: {}", driverId, e.getMessage());
        }
    }

    private void sendPositions() {
        Instant now = clock.instant();
        long tick = gpsTick++;
        long sent = 0;
        for (int i = 0; i < fleet.size(); i++) {
            try {
                client.publishPosition(fleet.positionAt(i, tick, now));
                sent++;
            } catch (Exception e) {
                log.warn("position send failed for {}: {}", fleet.driverIds().get(i), e.getMessage());
            }
        }
        metrics.positionsSent(sent);
    }

    private void createTrip() {
        try {
            client.createTrip(riderToken, new RideGoClient.TripRequest(
                    settings.cityId, pickupLat, pickupLng, dropoffLat, dropoffLng));
        } catch (Exception e) {
            log.warn("trip creation failed: {}", e.getMessage());
        }
    }

    private void drive(String offerId, String tripId, String driverId) {
        try {
            if (fleet.chance(tripId + ":" + offerId, SALT_REJECT, driverRejectRate)) {
                metrics.driverRejected();
                client.rejectOfferDirect(offerId, driverId);
                return;
            }

            metrics.acceptSent(tripId, clock.instant());
            if (!client.acceptOfferDirect(offerId, driverId)) {
                metrics.acceptFailed(tripId);
                return;
            }

            if (fleet.chance(tripId, SALT_CANCEL, riderCancelRate)) {
                Thread.sleep(500);
                client.cancel(riderToken, tripId, "rider_cancel_sim");
                return;
            }

            Thread.sleep(1000);
            client.arriveDirect(tripId, driverId);
            Thread.sleep(1000);
            client.startDirect(tripId, driverId);
            Thread.sleep(inProgressSeconds * 1000L);
            client.completeDirect(tripId, driverId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("drive failed for trip {}: {}", tripId, e.getMessage());
        }
    }

    private void printMetrics() {
        RunMetrics.Snapshot snapshot = metrics.snapshot();
        log.info("LOAD RESULT drivers={} gpsSent={} tripsRequested={} offers={} completed={} "
                        + "unmatched={} cancelled={} rejected={} acceptFailures={} "
                        + "matchRate={} completionRate={} p95MatchLatencyMs={} p95AcceptToEventMs={}",
                fleet.size(), snapshot.positionsSent(), snapshot.tripsRequested(), snapshot.offers(),
                snapshot.completed(), snapshot.unmatched(), snapshot.cancelled(), snapshot.rejected(),
                snapshot.acceptFailures(),
                String.format("%.3f", snapshot.matchRate()),
                String.format("%.3f", snapshot.completionRate()),
                snapshot.p95MatchLatencyMs(), snapshot.p95AcceptToEventMs());
    }
}

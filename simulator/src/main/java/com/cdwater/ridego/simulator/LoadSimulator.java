package com.cdwater.ridego.simulator;

import com.cdwater.ridego.contracts.DriverPositionPayload;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.InternalHeaders;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.contracts.Roles;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.messaging.EventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(prefix = "ridego.simulator", name = "scenario", havingValue = "load")
public class LoadSimulator {

    private static final Logger log = LoggerFactory.getLogger(LoadSimulator.class);

    private final RestClient gateway;
    private final RestClient locationClient;
    private final RestClient dispatchClient;
    private final RestClient tripClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final EventConsumer consumer;

    @Value("${ridego.simulator.load.drivers:10000}")
    private int drivers;
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
    @Value("${ridego.simulator.city-id:city-1}")
    private String cityId;
    @Value("${ridego.simulator.rider.username:rider1}")
    private String riderUsername;
    @Value("${ridego.simulator.rider.password:ridego}")
    private String riderPassword;

    private final List<String> driverIds = new ArrayList<>();
    private final Map<String, Instant> requestedAt = new ConcurrentHashMap<>();
    private final List<Long> matchLatenciesMs = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong tripsCreated = new AtomicLong();
    private final AtomicLong offers = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong unmatched = new AtomicLong();
    private final AtomicLong cancelled = new AtomicLong();
    private final AtomicLong acceptFailures = new AtomicLong();
    private final AtomicLong gpsSent = new AtomicLong();

    private ExecutorService offerExecutor;
    private ExecutorService presenceExecutor;
    private ScheduledExecutorService scheduler;
    private String riderToken;
    private int gridSide = 1;

    public LoadSimulator(KafkaTemplate<String, String> kafkaTemplate,
                         ObjectMapper objectMapper,
                         @Value("${ridego.gateway-url:http://localhost:8080/api/v1}") String gatewayUrl,
                         @Value("${ridego.simulator.location-url:http://localhost:8084}") String locationUrl,
                         @Value("${ridego.simulator.dispatch-url:http://localhost:8083}") String dispatchUrl,
                         @Value("${ridego.simulator.trip-url:http://localhost:8082}") String tripUrl) {
        this.gateway = RestClient.create(gatewayUrl);
        this.locationClient = RestClient.create(locationUrl);
        this.dispatchClient = RestClient.create(dispatchUrl);
        this.tripClient = RestClient.create(tripUrl);
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.consumer = new EventConsumer(
                new EventEnvelopeCodec(objectMapper, Clock.systemUTC()), "load-simulator")
                .on(EventTypes.TRIP_REQUESTED, event -> {
                    tripsCreated.incrementAndGet();
                    requestedAt.put(event.aggregateId(), event.occurredAt());
                })
                .on(EventTypes.OFFER_CREATED, OfferLifecyclePayload.class, event -> {
                    offers.incrementAndGet();
                    OfferLifecyclePayload payload = event.payload();
                    Instant requested = requestedAt.get(payload.tripId());
                    if (requested != null) {
                        matchLatenciesMs.add(Duration.between(requested, event.occurredAt()).toMillis());
                    }
                    try {
                        offerExecutor.submit(() ->
                                drive(payload.offerId(), payload.tripId(), payload.driverId()));
                    } catch (RejectedExecutionException ignored) {
                        // the run is shutting down; late offers are not driven
                    }
                })
                .on(EventTypes.TRIP_COMPLETED, event -> {
                    completed.incrementAndGet();
                    requestedAt.remove(event.aggregateId());
                })
                .on(EventTypes.TRIP_UNMATCHED, event -> {
                    unmatched.incrementAndGet();
                    requestedAt.remove(event.aggregateId());
                })
                .on(EventTypes.TRIP_CANCELLED, event -> {
                    cancelled.incrementAndGet();
                    requestedAt.remove(event.aggregateId());
                });
    }

    public void run() throws Exception {
        log.info("load scenario: drivers={} gpsInterval={}s tripRate={}/min duration={}min",
                drivers, gpsIntervalSeconds, tripRatePerMinute, durationMinutes);

        riderToken = objectMapper.readTree(gateway.post().uri("/auth/login")
                        .header("Content-Type", "application/json")
                        .body(Map.of("username", riderUsername, "password", riderPassword))
                        .retrieve().body(String.class))
                .get("token").asText();

        offerExecutor = Executors.newFixedThreadPool(64);
        presenceExecutor = Executors.newFixedThreadPool(64);
        scheduler = Executors.newScheduledThreadPool(2);

        bringDriversOnline();
        gridSide = Math.max(1, (int) Math.ceil(Math.sqrt(driverIds.size())));
        Thread.sleep(3000);

        scheduler.scheduleAtFixedRate(this::sendPositions, 0, gpsIntervalSeconds, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::createTrip, 0,
                60_000 / Math.max(1, tripRatePerMinute), TimeUnit.MILLISECONDS);

        Thread.sleep((long) (durationMinutes * 60_000));
        scheduler.shutdownNow();
        offerExecutor.shutdown();
        presenceExecutor.shutdown();
        offerExecutor.awaitTermination(15, TimeUnit.SECONDS);

        printMetrics();
    }

    @KafkaListener(topics = {Topics.DISPATCH_EVENTS, Topics.TRIP_EVENTS},
            groupId = "load-simulator-#{T(java.util.UUID).randomUUID()}")
    public void onEvent(String message) {
        consumer.consume(message);
    }

    private void bringDriversOnline() throws Exception {
        List<Future<?>> futures = new ArrayList<>(drivers);
        for (int i = 0; i < drivers; i++) {
            String driverId = "sim-driver-" + i;
            driverIds.add(driverId);
            futures.add(presenceExecutor.submit(() -> bringOnline(driverId)));
        }
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        log.info("{} drivers online", drivers);
    }

    private void bringOnline(String driverId) {
        try {
            locationClient.post().uri("/drivers/" + driverId + "/presence")
                    .header(InternalHeaders.USER_ID, driverId)
                    .header(InternalHeaders.DRIVER_ID, driverId)
                    .header(InternalHeaders.ROLE, Roles.DRIVER)
                    .header("Content-Type", "application/json")
                    .body(Map.of("online", true, "cityId", cityId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("presence failed for {}: {}", driverId, e.getMessage());
        }
    }

    private void sendPositions() {
        long sent = 0;
        for (int i = 0; i < driverIds.size(); i++) {
            String driverId = driverIds.get(i);
            int x = i % gridSide;
            int y = i / gridSide;
            double lat = pickupLat + (x - gridSide / 2.0) * 0.002
                    + ThreadLocalRandom.current().nextDouble(-0.0005, 0.0005);
            double lng = pickupLng + (y - gridSide / 2.0) * 0.002
                    + ThreadLocalRandom.current().nextDouble(-0.0005, 0.0005);
            try {
                kafkaTemplate.send(Topics.DRIVER_POSITIONS, driverId, objectMapper.writeValueAsString(
                        new DriverPositionPayload(driverId, cityId, lat, lng, Instant.now())));
                sent++;
            } catch (Exception e) {
                log.warn("position send failed for {}: {}", driverId, e.getMessage());
            }
        }
        gpsSent.addAndGet(sent);
    }

    private void createTrip() {
        try {
            gateway.post().uri("/trips")
                    .header("Authorization", "Bearer " + riderToken)
                    .header("Content-Type", "application/json")
                    .body(Map.of("cityId", cityId,
                            "pickupLat", pickupLat, "pickupLng", pickupLng,
                            "dropoffLat", dropoffLat, "dropoffLng", dropoffLng))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("trip creation failed: {}", e.getMessage());
        }
    }

    private void drive(String offerId, String tripId, String driverId) {
        try {
            if (!postDriver(dispatchClient, "/offers/" + offerId + "/accept", driverId)) {
                acceptFailures.incrementAndGet();
                return;
            }
            Thread.sleep(1000);
            postDriver(tripClient, "/trips/" + tripId + "/arrive", driverId);
            Thread.sleep(1000);
            postDriver(tripClient, "/trips/" + tripId + "/start", driverId);
            Thread.sleep(inProgressSeconds * 1000L);
            postDriver(tripClient, "/trips/" + tripId + "/complete", driverId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("drive failed for trip {}: {}", tripId, e.getMessage());
        }
    }

    private boolean postDriver(RestClient client, String path, String driverId) {
        try {
            client.post().uri(path)
                    .header(InternalHeaders.USER_ID, driverId)
                    .header(InternalHeaders.DRIVER_ID, driverId)
                    .header(InternalHeaders.ROLE, Roles.DRIVER)
                    .header("Content-Type", "application/json")
                    .body(Map.of())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void printMetrics() {
        long created = tripsCreated.get();
        long completedCount = completed.get();
        long unmatchedCount = unmatched.get();
        long cancelledCount = cancelled.get();
        double matchRate = created == 0 ? 0 : (double) completedCount / created;

        List<Long> latencies;
        synchronized (matchLatenciesMs) {
            latencies = new ArrayList<>(matchLatenciesMs);
        }
        Collections.sort(latencies);
        long p95 = latencies.isEmpty() ? -1 : latencies.get((int) Math.min(latencies.size() - 1,
                Math.ceil(latencies.size() * 0.95) - 1));

        log.info("LOAD RESULT drivers={} gpsSent={} tripsCreated={} offers={} completed={} "
                        + "unmatched={} cancelled={} acceptFailures={} matchRate={} p95MatchLatencyMs={}",
                drivers, gpsSent.get(), created, offers.get(), completedCount,
                unmatchedCount, cancelledCount, acceptFailures.get(),
                String.format("%.3f", matchRate), p95);
    }
}

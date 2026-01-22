package com.cdwater.ridego.simulator;

import com.cdwater.ridego.contracts.DriverPositionPayload;
import com.cdwater.ridego.contracts.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

@Component
public class SimulatorRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulatorRunner.class);

    private final RestClient http;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<LoadSimulator> loadSimulator;

    @Value("${ridego.simulator.scenario:happy}")
    private String scenario;
    @Value("${ridego.simulator.city-id:city-1}")
    private String cityId;
    @Value("${ridego.simulator.rider.username:rider1}")
    private String riderUsername;
    @Value("${ridego.simulator.rider.password:ridego}")
    private String riderPassword;
    @Value("${ridego.simulator.driver.username:driver1}")
    private String driverUsername;
    @Value("${ridego.simulator.driver.password:ridego}")
    private String driverPassword;
    @Value("${ridego.simulator.pickup-lat:31.2304}")
    private double pickupLat;
    @Value("${ridego.simulator.pickup-lng:121.4737}")
    private double pickupLng;
    @Value("${ridego.simulator.dropoff-lat:31.2400}")
    private double dropoffLat;
    @Value("${ridego.simulator.dropoff-lng:121.4900}")
    private double dropoffLng;
    @Value("${ridego.simulator.offer-timeout-seconds:20}")
    private int offerTimeoutSeconds;
    @Value("${ridego.simulator.heartbeat-wait-seconds:45}")
    private int heartbeatWaitSeconds;

    public SimulatorRunner(KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper,
                           ObjectProvider<LoadSimulator> loadSimulator,
                           @Value("${ridego.gateway-url:http://localhost:8080/api/v1}") String gatewayUrl) {
        this.http = RestClient.create(gatewayUrl);
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.loadSimulator = loadSimulator;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            switch (scenario) {
                case "cancel" -> runCancelScenario();
                case "timeout" -> runTimeoutScenario();
                case "heartbeat" -> runHeartbeatScenario();
                case "load" -> loadSimulator.getObject().run();
                default -> runHappyPath();
            }
            System.exit(0);
        } catch (Exception e) {
            log.error("simulation failed", e);
            System.exit(1);
        }
    }

    private void runHappyPath() throws Exception {
        Session session = prepare();
        String tripId = createTrip(session.riderToken());
        log.info("trip {} requested", tripId);

        String offerId = waitForOffer(session.driverToken());
        log.info("driver accepted offer {}", offerId);
        post(session.driverToken(), "/offers/" + offerId + "/accept", Map.of());

        Thread.sleep(1500);
        post(session.driverToken(), "/trips/" + tripId + "/arrive", Map.of());
        post(session.driverToken(), "/trips/" + tripId + "/start", Map.of());
        log.info("trip {} in progress", tripId);

        Thread.sleep(1000);
        publishPosition(session.driverId());
        Thread.sleep(500);
        post(session.driverToken(), "/trips/" + tripId + "/complete", Map.of());

        JsonNode completed = waitForState(session.riderToken(), tripId, "completed", 20);
        log.info("trip {} completed: {}", tripId, completed);

        JsonNode invoice = waitForInvoice(session.riderToken(), tripId);
        log.info("invoice for trip {}: amount={} state={}",
                tripId, invoice.get("amountCents").asInt(), invoice.get("state").asText());
        log.info("happy path OK");
    }

    private void runCancelScenario() throws Exception {
        Session session = prepare();
        String tripId = createTrip(session.riderToken());
        String offerId = waitForOffer(session.driverToken());
        post(session.driverToken(), "/offers/" + offerId + "/accept", Map.of());
        waitForState(session.riderToken(), tripId, "accepted", 20);
        log.info("trip {} accepted, rider cancels", tripId);

        cancel(session.riderToken(), tripId, "rider_changed_mind");
        waitForState(session.riderToken(), tripId, "cancelled", 20);
        log.info("trip {} cancelled", tripId);

        Thread.sleep(1000);
        String secondTrip = createTrip(session.riderToken());
        String secondOffer = waitForOffer(session.driverToken());
        log.info("driver released and re-offered on trip {} (offer {})", secondTrip, secondOffer);
        post(session.driverToken(), "/offers/" + secondOffer + "/accept", Map.of());
        cancel(session.riderToken(), secondTrip, "cleanup");
        waitForState(session.riderToken(), secondTrip, "cancelled", 20);
        log.info("cancel scenario OK");
    }

    private void runTimeoutScenario() throws Exception {
        Session session = prepare();
        String tripId = createTrip(session.riderToken());
        String offerId = waitForOffer(session.driverToken());
        log.info("trip {} offered to driver ({}), driver ignores it", tripId, offerId);

        waitForState(session.riderToken(), tripId, "unmatched", 90);
        log.info("trip {} unmatched after matching window", tripId);
        log.info("timeout scenario OK");
    }

    private void runHeartbeatScenario() throws Exception {
        Session session = prepare();
        log.info("driver will stop sending GPS; waiting {}s for heartbeat expiry",
                heartbeatWaitSeconds);
        Thread.sleep(heartbeatWaitSeconds * 1000L);

        String tripId = createTrip(session.riderToken());
        waitForState(session.riderToken(), tripId, "unmatched", 90);
        log.info("trip {} unmatched; heartbeat expiry OK", tripId);
    }

    private Session prepare() throws Exception {
        JsonNode rider = login(riderUsername, riderPassword);
        JsonNode driver = login(driverUsername, driverPassword);

        String riderToken = rider.get("token").asText();
        String driverToken = driver.get("token").asText();
        String driverId = driver.get("driverId").asText();

        log.info("driver {} online at city {}", driverId, cityId);
        post(driverToken, "/drivers/" + driverId + "/presence",
                Map.of("online", true, "cityId", cityId));
        publishPosition(driverId);
        Thread.sleep(1500);
        return new Session(riderToken, driverToken, driverId);
    }

    private void publishPosition(String driverId) {
        kafkaTemplate.send(Topics.DRIVER_POSITIONS, driverId, objectMapper.writeValueAsString(
                new DriverPositionPayload(driverId, cityId, pickupLat, pickupLng, Instant.now())));
    }

    private String createTrip(String riderToken) {
        return postForJson(riderToken, "/trips", Map.of(
                "cityId", cityId,
                "pickupLat", pickupLat, "pickupLng", pickupLng,
                "dropoffLat", dropoffLat, "dropoffLng", dropoffLng)).get("tripId").asText();
    }

    private String waitForOffer(String driverToken) throws InterruptedException {
        long deadline = System.currentTimeMillis() + offerTimeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            JsonNode offers = getJson(driverToken, "/offers/pending");
            if (offers.isArray() && !offers.isEmpty()) {
                return offers.get(0).get("offerId").asText();
            }
            Thread.sleep(300);
        }
        throw new IllegalStateException("no offer within " + offerTimeoutSeconds + "s");
    }

    private JsonNode waitForState(String token, String tripId, String expected, int timeoutSeconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                JsonNode trip = getJson(token, "/trips/" + tripId);
                if (expected.equals(trip.get("state").asText())) {
                    return trip;
                }
            } catch (HttpClientErrorException.NotFound ignored) {
                // read model projection has not caught up yet
            }
            Thread.sleep(300);
        }
        throw new IllegalStateException("trip did not reach state " + expected + " within " + timeoutSeconds + "s");
    }

    private JsonNode waitForInvoice(String token, String tripId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                return getJson(token, "/invoices/" + tripId);
            } catch (HttpClientErrorException.NotFound e) {
                Thread.sleep(300);
            }
        }
        throw new IllegalStateException("invoice not found within 10s");
    }

    private JsonNode login(String username, String password) {
        return postForJson(null, "/auth/login", Map.of("username", username, "password", password));
    }

    private JsonNode postForJson(String token, String path, Object body) {
        RestClient.RequestBodySpec request = http.post().uri(path)
                .header("Content-Type", "application/json");
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        String response = request.body(body).retrieve().body(String.class);
        return objectMapper.readTree(response);
    }

    private void post(String token, String path, Object body) {
        http.post().uri(path)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private void cancel(String token, String tripId, String reason) {
        post(token, "/trips/" + tripId + "/cancel", Map.of("reason", reason));
    }

    private JsonNode getJson(String token, String path) {
        String response = http.get().uri(path)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(response);
    }

    private record Session(String riderToken, String driverToken, String driverId) {
    }
}

package com.cdwater.ridego.simulator;

import com.cdwater.ridego.contracts.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
public class SimulatorRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulatorRunner.class);

    private final RestClient http;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

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

    public SimulatorRunner(KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper,
                           @Value("${ridego.gateway-url:http://localhost:8080/api/v1}") String gatewayUrl) {
        this.http = RestClient.create(gatewayUrl);
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            runTrip();
            System.exit(0);
        } catch (Exception e) {
            log.error("simulation failed", e);
            System.exit(1);
        }
    }

    private void runTrip() throws Exception {
        JsonNode rider = login(riderUsername, riderPassword);
        JsonNode driver = login(driverUsername, driverPassword);

        String riderToken = rider.get("token").asText();
        String driverToken = driver.get("token").asText();
        String driverId = driver.get("driverId").asText();

        log.info("driver {} online at city {}", driverId, cityId);
        post(driverToken, "/drivers/" + driverId + "/presence",
                Map.of("online", true, "cityId", cityId));
        kafkaTemplate.send(Topics.DRIVER_POSITIONS, driverId, objectMapper.writeValueAsString(
                Map.of("driverId", driverId, "cityId", cityId, "lat", pickupLat, "lng", pickupLng)));
        Thread.sleep(1500);

        String tripId = postForJson(riderToken, "/trips", Map.of(
                "cityId", cityId,
                "pickupLat", pickupLat, "pickupLng", pickupLng,
                "dropoffLat", dropoffLat, "dropoffLng", dropoffLng)).get("tripId").asText();
        log.info("trip {} requested", tripId);

        String offerId = waitForOffer(driverToken);
        log.info("driver accepted offer {}", offerId);
        post(driverToken, "/offers/" + offerId + "/accept", Map.of());

        Thread.sleep(1500);
        post(driverToken, "/trips/" + tripId + "/arrive", Map.of());
        post(driverToken, "/trips/" + tripId + "/start", Map.of());
        log.info("trip {} in progress", tripId);

        Thread.sleep(1000);
        post(driverToken, "/trips/" + tripId + "/complete", Map.of());

        JsonNode completed = waitForState(riderToken, tripId, "completed");
        log.info("trip {} completed: {}", tripId, completed);

        JsonNode invoice = waitForInvoice(riderToken, tripId);
        log.info("invoice for trip {}: amount={} state={}",
                tripId, invoice.get("amountCents").asInt(), invoice.get("state").asText());
        log.info("simulation OK");
    }

    private JsonNode login(String username, String password) {
        return postForJson(null, "/auth/login", Map.of("username", username, "password", password));
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

    private JsonNode waitForState(String token, String tripId, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000L;
        while (System.currentTimeMillis() < deadline) {
            JsonNode trip = getJson(token, "/trips/" + tripId);
            if (expected.equals(trip.get("state").asText())) {
                return trip;
            }
            Thread.sleep(300);
        }
        throw new IllegalStateException("trip did not reach state " + expected);
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
        RestClient.RequestBodySpec request = http.post().uri(path)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        request.body(body).retrieve().toBodilessEntity();
    }

    private JsonNode getJson(String token, String path) {
        String response = http.get().uri(path)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(response);
    }
}

package com.cdwater.ridego.simulator.client;

import com.cdwater.ridego.contracts.DriverPositionPayload;
import com.cdwater.ridego.contracts.InternalHeaders;
import com.cdwater.ridego.contracts.Roles;
import com.cdwater.ridego.contracts.Topics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.EnumMap;

/**
 * The only place the simulator talks to RideGo: gateway calls for rider/driver
 * scenarios, direct internal calls for load generation, and Kafka for GPS.
 */
public class RideGoClient {

    public enum Service {
        LOCATION,
        DISPATCH,
        TRIP
    }

    private final RestClient gateway;
    private final Map<Service, RestClient> internal;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public RideGoClient(String gatewayUrl,
                        String locationUrl,
                        String dispatchUrl,
                        String tripUrl,
                        KafkaTemplate<String, String> kafkaTemplate,
                        ObjectMapper objectMapper) {
        this.gateway = RestClient.create(gatewayUrl);
        this.internal = new EnumMap<>(Service.class);
        this.internal.put(Service.LOCATION, RestClient.create(locationUrl));
        this.internal.put(Service.DISPATCH, RestClient.create(dispatchUrl));
        this.internal.put(Service.TRIP, RestClient.create(tripUrl));
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public Login login(String username, String password) {
        JsonNode body = postForJson(null, "/auth/login",
                Map.of("username", username, "password", password));
        JsonNode driverId = body.get("driverId");
        return new Login(
                body.get("token").asText(),
                driverId == null || driverId.isNull() ? null : driverId.asText());
    }

    public String createTripId(String token, TripRequest request) {
        return postForJson(token, "/trips", request).get("tripId").asText();
    }

    public void createTrip(String token, TripRequest request) {
        post(token, "/trips", request);
    }

    public JsonNode trip(String token, String tripId) {
        return getJson(token, "/trips/" + tripId);
    }

    public JsonNode invoice(String token, String tripId) {
        return getJson(token, "/invoices/" + tripId);
    }

    public void cancel(String token, String tripId, String reason) {
        post(token, "/trips/" + tripId + "/cancel", Map.of("reason", reason));
    }

    public void acceptOffer(String token, String offerId) {
        post(token, "/offers/" + offerId + "/accept", Map.of());
    }

    public void arrive(String token, String tripId) {
        post(token, "/trips/" + tripId + "/arrive", Map.of());
    }

    public void start(String token, String tripId) {
        post(token, "/trips/" + tripId + "/start", Map.of());
    }

    public void complete(String token, String tripId) {
        post(token, "/trips/" + tripId + "/complete", Map.of());
    }

    public void setPresence(String driverToken, String driverId, String cityId) {
        post(driverToken, "/drivers/" + driverId + "/presence",
                Map.of("online", true, "cityId", cityId));
    }

    public void setPresenceDirect(String driverId, String cityId) {
        driverRequest(Service.LOCATION, "/drivers/" + driverId + "/presence", driverId)
                .body(Map.of("online", true, "cityId", cityId))
                .retrieve()
                .toBodilessEntity();
    }

    public boolean acceptOfferDirect(String offerId, String driverId) {
        try {
            driverRequest(Service.DISPATCH, "/offers/" + offerId + "/accept", driverId)
                    .body(Map.of())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void rejectOfferDirect(String offerId, String driverId) {
        driverRequest(Service.DISPATCH, "/offers/" + offerId + "/reject", driverId)
                .body(Map.of())
                .retrieve()
                .toBodilessEntity();
    }

    public void arriveDirect(String tripId, String driverId) {
        driverRequest(Service.TRIP, "/trips/" + tripId + "/arrive", driverId)
                .body(Map.of())
                .retrieve()
                .toBodilessEntity();
    }

    public void startDirect(String tripId, String driverId) {
        driverRequest(Service.TRIP, "/trips/" + tripId + "/start", driverId)
                .body(Map.of())
                .retrieve()
                .toBodilessEntity();
    }

    public void completeDirect(String tripId, String driverId) {
        driverRequest(Service.TRIP, "/trips/" + tripId + "/complete", driverId)
                .body(Map.of())
                .retrieve()
                .toBodilessEntity();
    }

    public void publishPosition(DriverPositionPayload position) {
        kafkaTemplate.send(Topics.DRIVER_POSITIONS, position.driverId(),
                objectMapper.writeValueAsString(position));
    }

    public String waitForOffer(String driverToken, int timeoutSeconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            JsonNode offers = getJson(driverToken, "/offers/pending");
            if (offers.isArray() && !offers.isEmpty()) {
                return offers.get(0).get("offerId").asText();
            }
            Thread.sleep(300);
        }
        throw new IllegalStateException("no offer within " + timeoutSeconds + "s");
    }

    public JsonNode waitForState(String token, String tripId, String expected, int timeoutSeconds)
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
        throw new IllegalStateException(
                "trip did not reach state " + expected + " within " + timeoutSeconds + "s");
    }

    public JsonNode waitForInvoice(String token, String tripId) throws InterruptedException {
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

    private RestClient.RequestBodySpec driverRequest(Service service,
                                                     String path,
                                                     String driverId) {
        return internal.get(service).post().uri(path)
                .header(InternalHeaders.USER_ID, driverId)
                .header(InternalHeaders.DRIVER_ID, driverId)
                .header(InternalHeaders.ROLE, Roles.DRIVER)
                .header("Content-Type", "application/json");
    }

    private JsonNode postForJson(String token, String path, Object body) {
        String response = authenticatedPost(token, path, body).retrieve().body(String.class);
        return objectMapper.readTree(response);
    }

    private void post(String token, String path, Object body) {
        authenticatedPost(token, path, body).retrieve().toBodilessEntity();
    }

    private RestClient.RequestBodySpec authenticatedPost(String token, String path, Object body) {
        RestClient.RequestBodySpec request = gateway.post().uri(path)
                .header("Content-Type", "application/json");
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return request.body(body);
    }

    private JsonNode getJson(String token, String path) {
        String response = gateway.get().uri(path)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(response);
    }

    public record Login(String token, String driverId) {
    }

    public record TripRequest(String cityId,
                              double pickupLat,
                              double pickupLng,
                              double dropoffLat,
                              double dropoffLng) {
    }
}

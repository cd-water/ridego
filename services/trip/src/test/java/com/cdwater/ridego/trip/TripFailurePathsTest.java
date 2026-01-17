package com.cdwater.ridego.trip;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.InternalHeaders;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.contracts.Roles;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.contracts.TripLifecyclePayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ridego.matching.window-seconds=10")
@Testcontainers
class TripFailurePathsTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16.15"))
                    .withDatabaseName("ridego_trip");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.2"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void riderCanCancelTrip() throws Exception {
        String riderId = UUID.randomUUID().toString();
        String tripId = createTrip(riderId);
        awaitState(riderId, null, tripId, "requested");

        HttpResponse<String> cancelled = post("/trips/" + tripId + "/cancel",
                objectMapper.writeValueAsString(Map.of("reason", "changed_mind")),
                InternalHeaders.USER_ID, riderId, InternalHeaders.ROLE, Roles.RIDER);
        assertEquals(204, cancelled.statusCode());
        awaitState(riderId, null, tripId, "cancelled");
    }

    @Test
    void driverCancelReturnsTripToMatchingAndDuplicateAcceptIsRejected() throws Exception {
        String riderId = UUID.randomUUID().toString();
        String tripId = createTrip(riderId);
        awaitState(riderId, null, tripId, "requested");

        sendDispatchEvent(EventTypes.MATCHING_STARTED, tripId,
                new TripLifecyclePayload(tripId));
        awaitState(riderId, null, tripId, "matching");

        String offerId = UUID.randomUUID().toString();
        sendDispatchEvent(EventTypes.OFFER_CREATED, tripId,
                new OfferLifecyclePayload(offerId, tripId, "driver-1", Instant.now().plusSeconds(30)));
        awaitState(riderId, null, tripId, "offered");

        sendDispatchEvent(EventTypes.OFFER_ACCEPTED, tripId,
                new OfferLifecyclePayload(offerId, tripId, "driver-1", null));
        awaitState(riderId, "driver-1", tripId, "accepted");

        HttpResponse<String> cancelled = post("/trips/" + tripId + "/cancel",
                objectMapper.writeValueAsString(Map.of("reason", "driver_cancel")),
                InternalHeaders.DRIVER_ID, "driver-1", InternalHeaders.ROLE, Roles.DRIVER);
        assertEquals(204, cancelled.statusCode());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            JsonNode trip = readTrip(riderId, null, tripId);
            assertEquals("matching", trip.get("state").asText());
            JsonNode driverIdNode = trip.get("driverId");
            assertTrue(driverIdNode == null || driverIdNode.isNull());
        });

        sendDispatchEvent(EventTypes.OFFER_ACCEPTED, tripId,
                new OfferLifecyclePayload(offerId, tripId, "driver-1", null));
        Thread.sleep(1000);

        assertEquals("matching", readTrip(riderId, null, tripId).get("state").asText());
    }

    @Test
    void matchingWindowExpiresToUnmatched() throws Exception {
        String riderId = UUID.randomUUID().toString();
        String tripId = createTrip(riderId);
        awaitState(riderId, null, tripId, "unmatched", 20);
    }

    private String createTrip(String riderId) throws Exception {
        String body = """
                {"cityId":"city-1","pickupLat":31.2304,"pickupLng":121.4737,
                 "dropoffLat":31.2400,"dropoffLng":121.4900}
                """;
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/trips"))
                .header("Content-Type", "application/json")
                .header(InternalHeaders.USER_ID, riderId)
                .header(InternalHeaders.ROLE, Roles.RIDER)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode());
        return objectMapper.readTree(response.body()).get("tripId").asText();
    }

    private void sendDispatchEvent(String eventType, String tripId, Object payload) throws Exception {
        EventEnvelope<Object> event = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                1,
                Instant.now(),
                tripId,
                "city-1",
                tripId,
                null,
                payload);
        kafkaTemplate.send(Topics.DISPATCH_EVENTS, tripId, objectMapper.writeValueAsString(event))
                .get(5, TimeUnit.SECONDS);
    }

    private void awaitState(String userId, String driverId, String tripId, String expected) {
        awaitState(userId, driverId, tripId, expected, 10);
    }

    private void awaitState(String userId, String driverId, String tripId, String expected, int timeoutSeconds) {
        await().atMost(Duration.ofSeconds(timeoutSeconds)).untilAsserted(() ->
                assertEquals(expected, readTrip(userId, driverId, tripId).get("state").asText()));
    }

    private JsonNode readTrip(String userId, String driverId, String tripId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/trips/" + tripId))
                .GET();
        if (userId != null) {
            builder.header(InternalHeaders.USER_ID, userId);
        }
        if (driverId != null) {
            builder.header(InternalHeaders.DRIVER_ID, driverId);
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> post(String path, String body, String header1, String value1,
                                      String header2, String value2) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header(header1, value1)
                .header(header2, value2);
        if (!InternalHeaders.USER_ID.equals(header1)
                && !InternalHeaders.USER_ID.equals(header2)) {
            builder.header(InternalHeaders.USER_ID, "test-user");
        }
        HttpRequest request = builder
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

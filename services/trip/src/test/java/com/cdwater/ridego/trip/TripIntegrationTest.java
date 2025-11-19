package com.cdwater.ridego.trip;

import com.cdwater.ridego.contracts.InternalHeaders;
import com.cdwater.ridego.contracts.Roles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TripIntegrationTest {

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

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void createdTripAppearsInReadModel() throws Exception {
        String riderId = UUID.randomUUID().toString();
        HttpResponse<String> created = createTrip(riderId);

        assertEquals(201, created.statusCode());
        String tripId = objectMapper.readTree(created.body()).get("tripId").asText();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            HttpResponse<String> fetched = getTrip(riderId, tripId);
            assertEquals(200, fetched.statusCode());
            JsonNode body = objectMapper.readTree(fetched.body());
            assertEquals("requested", body.get("state").asText());
        });
    }

    @Test
    void driverCannotReadTrip() throws Exception {
        String riderId = UUID.randomUUID().toString();
        String tripId = objectMapper.readTree(createTrip(riderId).body()).get("tripId").asText();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertEquals(200, getTrip(riderId, tripId).statusCode()));

        assertEquals(404, getTrip(UUID.randomUUID().toString(), tripId).statusCode());
    }

    private HttpResponse<String> createTrip(String riderId) throws Exception {
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
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getTrip(String riderId, String tripId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/trips/" + tripId))
                .header(InternalHeaders.USER_ID, riderId)
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

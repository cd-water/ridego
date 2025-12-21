package com.cdwater.ridego.messaging;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.TripRequestedPayload;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class OutboxIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16.15"));

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.2"));

    private JdbcTemplate jdbc;
    private EventEnvelopeCodec codec;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS outbox (
                    id bigserial PRIMARY KEY,
                    event_id uuid NOT NULL UNIQUE,
                    topic varchar(128) NOT NULL,
                    event_key varchar(128) NOT NULL,
                    payload jsonb NOT NULL,
                    occurred_at timestamptz NOT NULL,
                    published_at timestamptz,
                    attempts integer NOT NULL DEFAULT 0
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS processed_events (
                    consumer varchar(64) NOT NULL,
                    event_id uuid NOT NULL,
                    processed_at timestamptz NOT NULL,
                    PRIMARY KEY (consumer, event_id)
                )
                """);
        jdbc.update("TRUNCATE outbox, processed_events");
        codec = new EventEnvelopeCodec(
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void appendThenRelayPublishesEnvelope() {
        OutboxRepository outbox = new OutboxRepository(jdbc, codec);
        outbox.append("trip.events.v1", "trip-1", "city-1", EventTypes.TRIP_REQUESTED,
                new TripRequestedPayload("trip-1", "rider-1", "city-1",
                        31.2304, 121.4737, 31.24, 121.49));

        OutboxRelay relay = new OutboxRelay(outbox, kafkaTemplate());
        relay.relay();

        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of("trip.events.v1"));
            EventEnvelope<JsonNode>[] received = new EventEnvelope[1];
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                var records = consumer.poll(Duration.ofMillis(500));
                if (!records.isEmpty()) {
                    received[0] = codec.decode(records.iterator().next().value());
                }
                assertNotNull(received[0]);
            });
            assertEquals(EventTypes.TRIP_REQUESTED, received[0].eventType());
            assertEquals("trip-1", received[0].aggregateId());
        }

        Integer unpublished = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE published_at IS NULL", Integer.class);
        assertEquals(0, unpublished);
    }

    @Test
    void markOnceIsIdempotent() {
        ProcessedEvents processed = new ProcessedEvents(jdbc);
        String eventId = UUID.randomUUID().toString();

        assertTrue(processed.markOnce("test-consumer", eventId));
        assertFalse(processed.markOnce("test-consumer", eventId));
    }

    @Test
    void failedRelayIncrementsAttemptsAndLeavesRowUnpublished() {
        OutboxRepository outbox = new OutboxRepository(jdbc, codec);
        outbox.append("trip.events.v1", "trip-2", "city-1", EventTypes.TRIP_REQUESTED,
                new TripRequestedPayload("trip-2", "rider-1", "city-1",
                        31.2304, 121.4737, 31.24, 121.49));

        KafkaTemplate<String, String> failing = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(Map.<String, Object>of(
                        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            @Override
            public CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> send(
                    String topic, String key, String data) {
                return CompletableFuture.failedFuture(new RuntimeException("kafka down"));
            }
        };

        new OutboxRelay(outbox, failing).relay();

        Map<String, Object> row = jdbc.queryForMap("SELECT attempts, published_at FROM outbox");
        assertEquals(1, ((Number) row.get("attempts")).intValue());
        assertNull(row.get("published_at"));
    }

    private KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.<String, Object>of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
    }

    private KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }
}

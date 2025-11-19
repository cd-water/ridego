package com.cdwater.ridego.trip.projection;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.trip.config.KafkaTopics;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class TripProjectionListener {

    private static final String CONSUMER = "trip-projection";

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public TripProjectionListener(ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @KafkaListener(topics = KafkaTopics.TRIP_EVENTS, groupId = CONSUMER)
    @Transactional
    public void onMessage(String message) {
        EventEnvelope<JsonNode> event = objectMapper.readValue(
                message, new TypeReference<EventEnvelope<JsonNode>>() {
                });

        int inserted = jdbc.update("""
                        INSERT INTO processed_events (consumer, event_id, processed_at)
                        VALUES (?, ?, now())
                        ON CONFLICT DO NOTHING
                        """,
                CONSUMER, UUID.fromString(event.eventId()));
        if (inserted == 0) {
            return;
        }

        if (EventTypes.TRIP_REQUESTED.equals(event.eventType())) {
            JsonNode payload = event.payload();
            jdbc.update("""
                            INSERT INTO trip_read (id, city_id, rider_id, driver_id, state,
                                                   pickup_lat, pickup_lng, dropoff_lat, dropoff_lng, updated_at)
                            VALUES (?, ?, ?, NULL, 'requested', ?, ?, ?, ?, now())
                            ON CONFLICT (id) DO UPDATE
                            SET state = EXCLUDED.state, driver_id = EXCLUDED.driver_id, updated_at = now()
                            """,
                    UUID.fromString(payload.get("tripId").asText()),
                    payload.get("cityId").asText(),
                    payload.get("riderId").asText(),
                    payload.get("pickupLat").asDouble(),
                    payload.get("pickupLng").asDouble(),
                    payload.get("dropoffLat").asDouble(),
                    payload.get("dropoffLng").asDouble());
        }
    }
}

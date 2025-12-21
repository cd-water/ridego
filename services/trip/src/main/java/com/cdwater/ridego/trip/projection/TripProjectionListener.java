package com.cdwater.ridego.trip.projection;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.messaging.ProcessedEvents;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

@Component
public class TripProjectionListener {

    private static final String CONSUMER = "trip-projection";

    private final EventEnvelopeCodec codec;
    private final ProcessedEvents processedEvents;
    private final JdbcTemplate jdbc;

    public TripProjectionListener(EventEnvelopeCodec codec,
                                  ProcessedEvents processedEvents,
                                  JdbcTemplate jdbc) {
        this.codec = codec;
        this.processedEvents = processedEvents;
        this.jdbc = jdbc;
    }

    @KafkaListener(topics = Topics.TRIP_EVENTS, groupId = CONSUMER)
    @Transactional
    public void onMessage(String message) {
        EventEnvelope<JsonNode> event = codec.decode(message);

        if (!processedEvents.markOnce(CONSUMER, event.eventId())) {
            return;
        }

        if (EventTypes.TRIP_REQUESTED.equals(event.eventType())) {
            projectRequested(event);
            return;
        }

        String state = stateFor(event.eventType());
        if (state == null) {
            return;
        }

        JsonNode payload = event.payload();
        String driverId = payload.hasNonNull("driverId") ? payload.get("driverId").asText() : null;
        UUID tripId = UUID.fromString(event.aggregateId());

        if (EventTypes.TRIP_MATCHING_RESTARTED.equals(event.eventType())
                || EventTypes.TRIP_DRIVER_CANCELLED.equals(event.eventType())) {
            jdbc.update("""
                            UPDATE trip_read SET state = ?, driver_id = NULL, updated_at = now()
                            WHERE id = ?
                            """,
                    state, tripId);
        } else {
            jdbc.update("""
                            UPDATE trip_read SET state = ?, driver_id = COALESCE(?, driver_id), updated_at = now()
                            WHERE id = ?
                            """,
                    state, driverId, tripId);
        }
    }

    private void projectRequested(EventEnvelope<JsonNode> event) {
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

    private String stateFor(String eventType) {
        return switch (eventType) {
            case EventTypes.TRIP_MATCHING_STARTED -> "matching";
            case EventTypes.TRIP_OFFERED -> "offered";
            case EventTypes.TRIP_MATCHING_RESTARTED -> "matching";
            case EventTypes.TRIP_DRIVER_CANCELLED -> "matching";
            case EventTypes.TRIP_ACCEPTED -> "accepted";
            case EventTypes.TRIP_ARRIVED -> "arrived";
            case EventTypes.TRIP_STARTED -> "in_progress";
            case EventTypes.TRIP_COMPLETED -> "completed";
            case EventTypes.TRIP_CANCELLED -> "cancelled";
            case EventTypes.TRIP_UNMATCHED -> "unmatched";
            default -> null;
        };
    }
}

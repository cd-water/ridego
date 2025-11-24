package com.cdwater.ridego.billing.outbox;

import com.cdwater.ridego.contracts.EventEnvelope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OutboxRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void append(String topic, String key, EventEnvelope<?> event) {
        try {
            jdbc.update("""
                            INSERT INTO outbox (event_id, topic, event_key, payload, occurred_at)
                            VALUES (?, ?, ?, ?::jsonb, ?)
                            """,
                    UUID.fromString(event.eventId()),
                    topic,
                    key,
                    objectMapper.writeValueAsString(event),
                    Timestamp.from(event.occurredAt()));
        } catch (JacksonException e) {
            throw new IllegalStateException("failed to serialize event " + event.eventId(), e);
        }
    }

    public List<OutboxRow> findUnpublished(int limit) {
        return jdbc.query("""
                        SELECT id, topic, event_key, payload::text AS payload
                        FROM outbox
                        WHERE published_at IS NULL
                        ORDER BY id
                        LIMIT ?
                        """,
                (rs, rowNum) -> new OutboxRow(
                        rs.getLong("id"),
                        rs.getString("topic"),
                        rs.getString("event_key"),
                        rs.getString("payload")),
                limit);
    }

    public void markPublished(long id) {
        jdbc.update("UPDATE outbox SET published_at = now() WHERE id = ?", id);
    }

    public void markFailed(long id) {
        jdbc.update("UPDATE outbox SET attempts = attempts + 1 WHERE id = ?", id);
    }

    public record OutboxRow(long id, String topic, String key, String payload) {
    }
}

package com.cdwater.ridego.messaging;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class OutboxRepository {

    private final JdbcTemplate jdbc;
    private final EventEnvelopeCodec codec;

    public OutboxRepository(JdbcTemplate jdbc, EventEnvelopeCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    public void append(String topic, String key, String cityId, String eventType, Object payload) {
        EventEnvelope<Object> envelope = codec.envelope(eventType, key, cityId, key, null, payload);
        jdbc.update("""
                        INSERT INTO outbox (event_id, topic, event_key, payload, occurred_at)
                        VALUES (?, ?, ?, ?::jsonb, ?)
                        """,
                UUID.fromString(envelope.eventId()),
                topic,
                key,
                codec.encode(envelope),
                Timestamp.from(envelope.occurredAt()));
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

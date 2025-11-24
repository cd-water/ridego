package com.cdwater.ridego.dispatch.processed;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ProcessedEventRepository {

    private final JdbcTemplate jdbc;

    public ProcessedEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean markProcessed(String consumer, String eventId) {
        return jdbc.update("""
                        INSERT INTO processed_events (consumer, event_id, processed_at)
                        VALUES (?, ?, now())
                        ON CONFLICT DO NOTHING
                        """,
                consumer, UUID.fromString(eventId)) > 0;
    }
}

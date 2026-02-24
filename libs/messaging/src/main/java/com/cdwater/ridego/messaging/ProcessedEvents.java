package com.cdwater.ridego.messaging;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

public class ProcessedEvents implements EventDedup {

    private final JdbcTemplate jdbc;

    public ProcessedEvents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean markOnce(String consumer, String eventId) {
        return jdbc.update("""
                        INSERT INTO processed_events (consumer, event_id, processed_at)
                        VALUES (?, ?, now())
                        ON CONFLICT DO NOTHING
                        """,
                consumer, UUID.fromString(eventId)) > 0;
    }
}

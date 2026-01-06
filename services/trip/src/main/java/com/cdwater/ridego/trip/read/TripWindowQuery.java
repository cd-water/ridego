package com.cdwater.ridego.trip.read;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class TripWindowQuery {

    private final JdbcTemplate jdbc;

    public TripWindowQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UUID> findMatchingExpired(Instant now, int limit) {
        return jdbc.queryForList("""
                        SELECT id FROM trips
                        WHERE state IN ('requested', 'matching') AND matching_deadline <= ?
                        ORDER BY matching_deadline
                        LIMIT ?
                        """,
                UUID.class, Timestamp.from(now), limit);
    }
}

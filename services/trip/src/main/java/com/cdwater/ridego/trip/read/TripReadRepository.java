package com.cdwater.ridego.trip.read;

import com.cdwater.ridego.trip.trip.TripView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TripReadRepository {

    private final JdbcTemplate jdbc;

    public TripReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TripView> findById(UUID id) {
        return jdbc.query("""
                        SELECT id, city_id, rider_id, driver_id, state, pickup_lat, pickup_lng,
                               dropoff_lat, dropoff_lng, updated_at
                        FROM trip_read WHERE id = ?
                        """,
                (rs, rowNum) -> new TripView(
                        rs.getObject("id", UUID.class),
                        rs.getString("city_id"),
                        rs.getString("rider_id"),
                        rs.getString("driver_id"),
                        rs.getString("state"),
                        rs.getDouble("pickup_lat"),
                        rs.getDouble("pickup_lng"),
                        rs.getDouble("dropoff_lat"),
                        rs.getDouble("dropoff_lng"),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                id
        ).stream().findFirst();
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

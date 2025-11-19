package com.cdwater.ridego.trip.trip;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TripRepository {

    private final JdbcTemplate jdbc;

    public TripRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, String cityId, String riderId, double pickupLat, double pickupLng,
                       double dropoffLat, double dropoffLng, Instant requestedAt) {
        jdbc.update("""
                        INSERT INTO trips (id, city_id, rider_id, state, pickup_lat, pickup_lng,
                                           dropoff_lat, dropoff_lng, version, requested_at)
                        VALUES (?, ?, ?, 'requested', ?, ?, ?, ?, 0, ?)
                        """,
                id, cityId, riderId, pickupLat, pickupLng, dropoffLat, dropoffLng,
                Timestamp.from(requestedAt));
    }

    public Optional<TripView> findReadModel(UUID id) {
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
}

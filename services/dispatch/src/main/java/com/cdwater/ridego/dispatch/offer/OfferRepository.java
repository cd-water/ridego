package com.cdwater.ridego.dispatch.offer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OfferRepository {

    private static final RowMapper<Offer> ROW_MAPPER = (rs, rowNum) -> new Offer(
            rs.getObject("id", UUID.class),
            rs.getObject("trip_id", UUID.class),
            rs.getString("driver_id"),
            rs.getString("city_id"),
            rs.getString("state"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant()
    );

    private final JdbcTemplate jdbc;

    public OfferRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tripId, String driverId, String cityId,
                       Instant createdAt, Instant expiresAt) {
        jdbc.update("""
                        INSERT INTO offers (id, trip_id, driver_id, city_id, state, created_at, expires_at)
                        VALUES (?, ?, ?, ?, 'PENDING', ?, ?)
                        """,
                id, tripId, driverId, cityId,
                Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    public Optional<Offer> find(UUID id) {
        return jdbc.query("SELECT * FROM offers WHERE id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public List<Offer> pendingForDriver(String driverId, Instant now) {
        return jdbc.query("""
                        SELECT * FROM offers
                        WHERE driver_id = ? AND state = 'PENDING' AND expires_at > ?
                        ORDER BY created_at
                        """,
                ROW_MAPPER, driverId, Timestamp.from(now));
    }

    public List<Offer> findExpired(Instant now, int limit) {
        return jdbc.query("""
                        SELECT * FROM offers
                        WHERE state = 'PENDING' AND expires_at <= ?
                        ORDER BY expires_at
                        LIMIT ?
                        """,
                ROW_MAPPER, Timestamp.from(now), limit);
    }

    public List<Offer> findActiveForTrip(UUID tripId) {
        return jdbc.query("""
                        SELECT * FROM offers
                        WHERE trip_id = ? AND state IN ('PENDING', 'ACCEPTED')
                        """,
                ROW_MAPPER, tripId);
    }

    public int accept(UUID id) {
        return jdbc.update("""
                        UPDATE offers SET state = 'ACCEPTED', responded_at = now()
                        WHERE id = ? AND state = 'PENDING' AND expires_at > now()
                        """,
                id);
    }

    public int reject(UUID id) {
        return jdbc.update("""
                        UPDATE offers SET state = 'REJECTED', responded_at = now()
                        WHERE id = ? AND state = 'PENDING'
                        """,
                id);
    }

    public int expire(UUID id) {
        return jdbc.update("""
                        UPDATE offers SET state = 'EXPIRED', responded_at = now()
                        WHERE id = ? AND state = 'PENDING'
                        """,
                id);
    }

    public int release(UUID id) {
        return jdbc.update("""
                        UPDATE offers SET state = 'RELEASED', responded_at = now()
                        WHERE id = ? AND state IN ('PENDING', 'ACCEPTED')
                        """,
                id);
    }

    public int releaseForTripAndDriver(UUID tripId, String driverId) {
        return jdbc.update("""
                        UPDATE offers SET state = 'RELEASED', responded_at = now()
                        WHERE trip_id = ? AND driver_id = ? AND state IN ('PENDING', 'ACCEPTED')
                        """,
                tripId, driverId);
    }

    public java.util.Set<String> attemptedDrivers(UUID tripId) {
        return new java.util.HashSet<>(jdbc.queryForList("""
                        SELECT DISTINCT driver_id FROM offers
                        WHERE trip_id = ? AND state IN ('EXPIRED', 'REJECTED', 'RELEASED')
                        """,
                String.class, tripId));
    }
}

package com.cdwater.ridego.trip.trip;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TripRepository {

    private static final RowMapper<TripRecord> TRIP_MAPPER = (rs, rowNum) -> new TripRecord(
            rs.getObject("id", UUID.class),
            rs.getString("city_id"),
            rs.getString("rider_id"),
            rs.getString("driver_id"),
            rs.getString("state"),
            rs.getDouble("pickup_lat"),
            rs.getDouble("pickup_lng"),
            rs.getDouble("dropoff_lat"),
            rs.getDouble("dropoff_lng"),
            rs.getTimestamp("requested_at").toInstant(),
            toInstant(rs.getTimestamp("matching_deadline")),
            toInstant(rs.getTimestamp("accepted_at")),
            toInstant(rs.getTimestamp("arrived_at")),
            toInstant(rs.getTimestamp("started_at")),
            toInstant(rs.getTimestamp("completed_at"))
    );

    private final JdbcTemplate jdbc;

    public TripRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, String cityId, String riderId, double pickupLat, double pickupLng,
                       double dropoffLat, double dropoffLng, Instant requestedAt, Instant matchingDeadline) {
        jdbc.update("""
                        INSERT INTO trips (id, city_id, rider_id, state, pickup_lat, pickup_lng,
                                           dropoff_lat, dropoff_lng, version, requested_at, matching_deadline)
                        VALUES (?, ?, ?, 'requested', ?, ?, ?, ?, 0, ?, ?)
                        """,
                id, cityId, riderId, pickupLat, pickupLng, dropoffLat, dropoffLng,
                Timestamp.from(requestedAt), Timestamp.from(matchingDeadline));
    }

    public Optional<TripRecord> find(UUID id) {
        return jdbc.query("SELECT * FROM trips WHERE id = ?", TRIP_MAPPER, id)
                .stream().findFirst();
    }

    public List<TripRecord> findMatchingExpired(Instant now, int limit) {
        return jdbc.query("""
                        SELECT * FROM trips
                        WHERE state IN ('requested', 'matching') AND matching_deadline <= ?
                        ORDER BY matching_deadline
                        LIMIT ?
                        """,
                TRIP_MAPPER, Timestamp.from(now), limit);
    }

    public int updateToMatching(UUID id, String expectedState) {
        return jdbc.update("""
                        UPDATE trips SET state = 'matching', matching_started_at = now(), version = version + 1
                        WHERE id = ? AND state = ?
                        """,
                id, expectedState);
    }

    public int updateToOffered(UUID id, String expectedState, UUID offerId, String driverId) {
        return jdbc.update("""
                        UPDATE trips SET state = 'offered', offer_id = ?, driver_id = ?, version = version + 1
                        WHERE id = ? AND state = ?
                        """,
                offerId, driverId, id, expectedState);
    }

    public int updateToMatchingForRedispatch(UUID id, String expectedState, Instant deadline) {
        return jdbc.update("""
                        UPDATE trips SET state = 'matching', offer_id = NULL, driver_id = NULL,
                                         matching_deadline = ?, version = version + 1
                        WHERE id = ? AND state = ?
                        """,
                Timestamp.from(deadline), id, expectedState);
    }

    public int updateToAccepted(UUID id, String expectedState) {
        return jdbc.update("""
                        UPDATE trips SET state = 'accepted', accepted_at = now(), version = version + 1
                        WHERE id = ? AND state = ?
                        """,
                id, expectedState);
    }

    public int updateToArrived(UUID id, String expectedState) {
        return jdbc.update("""
                        UPDATE trips SET state = 'arrived', arrived_at = now(), version = version + 1
                        WHERE id = ? AND state = ?
                        """,
                id, expectedState);
    }

    public int updateToStarted(UUID id, String expectedState) {
        return jdbc.update("""
                        UPDATE trips SET state = 'in_progress', started_at = now(), version = version + 1
                        WHERE id = ? AND state = ?
                        """,
                id, expectedState);
    }

    public int updateToCompleted(UUID id, String expectedState) {
        return jdbc.update("""
                        UPDATE trips SET state = 'completed', completed_at = now(), version = version + 1
                        WHERE id = ? AND state = ?
                        """,
                id, expectedState);
    }

    public int updateToCancelled(UUID id, String expectedState, String actor, String reason) {
        return jdbc.update("""
                        UPDATE trips SET state = 'cancelled', cancelled_at = now(),
                                         cancel_actor = ?, cancel_reason = ?, version = version + 1
                        WHERE id = ? AND state = ?
                        """,
                actor, reason, id, expectedState);
    }

    public int updateToUnmatched(UUID id, String expectedState) {
        return jdbc.update("""
                        UPDATE trips SET state = 'unmatched', unmatched_at = now(), version = version + 1
                        WHERE id = ? AND state = ?
                        """,
                id, expectedState);
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

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

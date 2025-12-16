package com.cdwater.ridego.trip.persistence;

import com.cdwater.ridego.trip.lifecycle.Trip;
import com.cdwater.ridego.trip.lifecycle.TripState;
import com.cdwater.ridego.trip.lifecycle.TripStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcTripStore implements TripStore {

    private static final RowMapper<Trip> ROW_MAPPER = (rs, rowNum) -> new Trip(
            rs.getObject("id", UUID.class),
            rs.getString("city_id"),
            rs.getString("rider_id"),
            rs.getString("driver_id"),
            rs.getString("offer_id"),
            TripState.from(rs.getString("state")),
            rs.getDouble("pickup_lat"),
            rs.getDouble("pickup_lng"),
            rs.getDouble("dropoff_lat"),
            rs.getDouble("dropoff_lng"),
            toInstant(rs.getTimestamp("requested_at")),
            toInstant(rs.getTimestamp("matching_deadline")),
            toInstant(rs.getTimestamp("matching_started_at")),
            toInstant(rs.getTimestamp("accepted_at")),
            toInstant(rs.getTimestamp("arrived_at")),
            toInstant(rs.getTimestamp("started_at")),
            toInstant(rs.getTimestamp("completed_at")),
            toInstant(rs.getTimestamp("cancelled_at")),
            toInstant(rs.getTimestamp("unmatched_at")),
            rs.getString("cancel_actor"),
            rs.getString("cancel_reason"),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public JdbcTripStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Trip> load(UUID id) {
        return jdbc.query("SELECT * FROM trips WHERE id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public void insert(Trip trip) {
        jdbc.update("""
                        INSERT INTO trips (id, city_id, rider_id, driver_id, offer_id, state,
                                           pickup_lat, pickup_lng, dropoff_lat, dropoff_lng,
                                           version, requested_at, matching_deadline, matching_started_at,
                                           accepted_at, arrived_at, started_at, completed_at,
                                           cancelled_at, unmatched_at, cancel_actor, cancel_reason)
                        VALUES (?, ?, ?, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                trip.getId(), trip.getCityId(), trip.getRiderId(), trip.getDriverId(),
                trip.getOfferId(), trip.getState().wireValue(),
                trip.getPickupLat(), trip.getPickupLng(), trip.getDropoffLat(), trip.getDropoffLng(),
                trip.getVersion(), ts(trip.getRequestedAt()), ts(trip.getMatchingDeadline()),
                ts(trip.getMatchingStartedAt()), ts(trip.getAcceptedAt()), ts(trip.getArrivedAt()),
                ts(trip.getStartedAt()), ts(trip.getCompletedAt()), ts(trip.getCancelledAt()),
                ts(trip.getUnmatchedAt()), trip.getCancelActor(), trip.getCancelReason());
    }

    @Override
    public boolean save(Trip trip, String expectedState) {
        int updated = jdbc.update("""
                        UPDATE trips
                        SET state = ?, driver_id = ?, offer_id = ?::uuid, matching_deadline = ?,
                            matching_started_at = ?, accepted_at = ?, arrived_at = ?, started_at = ?,
                            completed_at = ?, cancelled_at = ?, unmatched_at = ?,
                            cancel_actor = ?, cancel_reason = ?, version = version + 1
                        WHERE id = ? AND state = ?
                        """,
                trip.getState().wireValue(), trip.getDriverId(), trip.getOfferId(),
                ts(trip.getMatchingDeadline()), ts(trip.getMatchingStartedAt()),
                ts(trip.getAcceptedAt()), ts(trip.getArrivedAt()), ts(trip.getStartedAt()),
                ts(trip.getCompletedAt()), ts(trip.getCancelledAt()), ts(trip.getUnmatchedAt()),
                trip.getCancelActor(), trip.getCancelReason(),
                trip.getId(), expectedState);
        return updated > 0;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}

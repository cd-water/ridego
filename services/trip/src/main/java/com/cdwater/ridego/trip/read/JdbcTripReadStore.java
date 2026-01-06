package com.cdwater.ridego.trip.read;

import com.cdwater.ridego.contracts.TripRequestedPayload;
import com.cdwater.ridego.trip.lifecycle.TripState;
import com.cdwater.ridego.trip.trip.TripView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcTripReadStore implements TripReadStore {

    private final JdbcTemplate jdbc;

    public JdbcTripReadStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(TripRequestedPayload trip) {
        jdbc.update("""
                        INSERT INTO trip_read (id, city_id, rider_id, driver_id, state,
                                               pickup_lat, pickup_lng, dropoff_lat, dropoff_lng, updated_at)
                        VALUES (?, ?, ?, NULL, 'requested', ?, ?, ?, ?, now())
                        ON CONFLICT (id) DO UPDATE
                        SET state = EXCLUDED.state, driver_id = EXCLUDED.driver_id, updated_at = now()
                        """,
                UUID.fromString(trip.tripId()),
                trip.cityId(),
                trip.riderId(),
                trip.pickupLat(),
                trip.pickupLng(),
                trip.dropoffLat(),
                trip.dropoffLng());
    }

    @Override
    public void updateState(UUID tripId, TripState state, String driverId, boolean clearDriver) {
        if (clearDriver) {
            jdbc.update("""
                            UPDATE trip_read SET state = ?, driver_id = NULL, updated_at = now()
                            WHERE id = ?
                            """,
                    state.wireValue(), tripId);
        } else {
            jdbc.update("""
                            UPDATE trip_read SET state = ?, driver_id = COALESCE(?, driver_id), updated_at = now()
                            WHERE id = ?
                            """,
                    state.wireValue(), driverId, tripId);
        }
    }

    @Override
    public Optional<TripView> find(UUID tripId) {
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
                tripId
        ).stream().findFirst();
    }
}

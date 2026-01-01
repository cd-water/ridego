package com.cdwater.ridego.dispatch.availability;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcAvailabilityStore implements AvailabilityStore {

    private final JdbcTemplate jdbc;

    public JdbcAvailabilityStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Availability> load(String driverId) {
        return jdbc.query("""
                        SELECT driver_id, city_id, state, presence
                        FROM driver_availability WHERE driver_id = ?
                        """,
                (rs, rowNum) -> new Availability(
                        rs.getString("driver_id"),
                        rs.getString("city_id"),
                        AvailabilityState.valueOf(rs.getString("state")),
                        Presence.valueOf(rs.getString("presence"))),
                driverId
        ).stream().findFirst();
    }

    @Override
    public void insert(Availability availability) {
        jdbc.update("""
                        INSERT INTO driver_availability (driver_id, city_id, state, presence, updated_at)
                        VALUES (?, ?, ?, ?, now())
                        """,
                availability.getDriverId(),
                availability.getCityId(),
                availability.getState().wireValue(),
                availability.getPresence().wireValue());
    }

    @Override
    public boolean save(Availability availability,
                        AvailabilityState expectedState,
                        Presence expectedPresence) {
        return jdbc.update("""
                        UPDATE driver_availability
                        SET state = ?, presence = ?, city_id = ?, updated_at = now()
                        WHERE driver_id = ? AND state = ? AND presence = ?
                        """,
                availability.getState().wireValue(),
                availability.getPresence().wireValue(),
                availability.getCityId(),
                availability.getDriverId(),
                expectedState.wireValue(),
                expectedPresence.wireValue()) > 0;
    }
}

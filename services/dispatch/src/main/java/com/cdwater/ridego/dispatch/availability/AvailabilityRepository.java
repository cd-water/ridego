package com.cdwater.ridego.dispatch.availability;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AvailabilityRepository {

    private final JdbcTemplate jdbc;

    public AvailabilityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void online(String driverId, String cityId) {
        jdbc.update("""
                        INSERT INTO driver_availability (driver_id, city_id, state, updated_at)
                        VALUES (?, ?, 'IDLE', now())
                        ON CONFLICT (driver_id) DO UPDATE
                        SET city_id = EXCLUDED.city_id,
                            state = CASE WHEN driver_availability.state = 'ON_TRIP'
                                         THEN 'ON_TRIP' ELSE 'IDLE' END,
                            updated_at = now()
                        """,
                driverId, cityId);
    }

    public void offline(String driverId, String cityId) {
        int updated = jdbc.update("""
                        UPDATE driver_availability SET state = 'OFFLINE', updated_at = now()
                        WHERE driver_id = ? AND state <> 'ON_TRIP'
                        """,
                driverId);
        if (updated == 0) {
            jdbc.update("""
                            INSERT INTO driver_availability (driver_id, city_id, state, updated_at)
                            VALUES (?, ?, 'OFFLINE', now())
                            ON CONFLICT (driver_id) DO NOTHING
                            """,
                    driverId, cityId);
        }
    }

    public boolean isIdle(String driverId, String cityId) {
        Integer count = jdbc.queryForObject("""
                        SELECT count(*) FROM driver_availability
                        WHERE driver_id = ? AND city_id = ? AND state = 'IDLE'
                        """,
                Integer.class, driverId, cityId);
        return count != null && count > 0;
    }

    public void move(String driverId, String from, String to) {
        jdbc.update("""
                        UPDATE driver_availability SET state = ?, updated_at = now()
                        WHERE driver_id = ? AND state = ?
                        """,
                to, driverId, from);
    }

    public void release(String driverId) {
        jdbc.update("""
                        UPDATE driver_availability SET state = 'IDLE', updated_at = now()
                        WHERE driver_id = ? AND state IN ('OFFERED', 'ON_TRIP')
                        """,
                driverId);
    }
}

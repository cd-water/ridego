package com.cdwater.ridego.dispatch.persistence;

import com.cdwater.ridego.dispatch.lifecycle.Offer;
import com.cdwater.ridego.dispatch.lifecycle.OfferState;
import com.cdwater.ridego.dispatch.lifecycle.OfferStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcOfferStore implements OfferStore {

    private static final RowMapper<Offer> ROW_MAPPER = (rs, rowNum) -> new Offer(
            rs.getObject("id", UUID.class),
            rs.getObject("trip_id", UUID.class),
            rs.getString("driver_id"),
            rs.getString("city_id"),
            OfferState.valueOf(rs.getString("state")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant(),
            toInstant(rs.getTimestamp("responded_at"))
    );

    private final JdbcTemplate jdbc;

    public JdbcOfferStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Offer> load(UUID offerId) {
        return jdbc.query("SELECT * FROM offers WHERE id = ?", ROW_MAPPER, offerId)
                .stream().findFirst();
    }

    @Override
    public List<Offer> pendingForDriver(String driverId) {
        return jdbc.query("""
                        SELECT * FROM offers
                        WHERE driver_id = ? AND state = 'PENDING' AND expires_at > now()
                        ORDER BY created_at
                        """,
                ROW_MAPPER, driverId);
    }

    @Override
    public List<Offer> findExpired(Instant now, int limit) {
        return jdbc.query("""
                        SELECT * FROM offers
                        WHERE state = 'PENDING' AND expires_at <= ?
                        ORDER BY expires_at
                        LIMIT ?
                        """,
                ROW_MAPPER, Timestamp.from(now), limit);
    }

    @Override
    public List<Offer> findActiveForTrip(UUID tripId) {
        return jdbc.query("""
                        SELECT * FROM offers
                        WHERE trip_id = ? AND state IN ('PENDING', 'ACCEPTED')
                        """,
                ROW_MAPPER, tripId);
    }

    @Override
    public Set<String> attemptedDrivers(UUID tripId) {
        return new HashSet<>(jdbc.queryForList("""
                        SELECT DISTINCT driver_id FROM offers
                        WHERE trip_id = ? AND state IN ('EXPIRED', 'REJECTED', 'RELEASED')
                        """,
                String.class, tripId));
    }

    @Override
    public boolean insert(Offer offer) {
        try {
            jdbc.update("""
                            INSERT INTO offers (id, trip_id, driver_id, city_id, state, created_at, expires_at)
                            VALUES (?, ?, ?, ?, 'PENDING', ?, ?)
                            """,
                    offer.getId(), offer.getTripId(), offer.getDriverId(), offer.getCityId(),
                    Timestamp.from(offer.getCreatedAt()), Timestamp.from(offer.getExpiresAt()));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public boolean save(Offer offer, String expectedState) {
        return jdbc.update("""
                        UPDATE offers SET state = ?, responded_at = ?
                        WHERE id = ? AND state = ?
                        """,
                offer.getState().wireValue(),
                toTimestamp(offer.getRespondedAt()),
                offer.getId(),
                expectedState) > 0;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}

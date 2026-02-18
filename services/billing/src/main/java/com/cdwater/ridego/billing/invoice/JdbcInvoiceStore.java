package com.cdwater.ridego.billing.invoice;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcInvoiceStore implements InvoiceStore {

    private static final RowMapper<Invoice> ROW_MAPPER = (rs, rowNum) -> new Invoice(
            rs.getObject("id", UUID.class),
            rs.getObject("trip_id", UUID.class),
            rs.getString("rider_id"),
            rs.getString("driver_id"),
            rs.getInt("amount_cents"),
            InvoiceState.valueOf(rs.getString("state")),
            rs.getTimestamp("issued_at").toInstant(),
            toInstant(rs.getTimestamp("paid_at"))
    );

    private final JdbcTemplate jdbc;

    public JdbcInvoiceStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Invoice> findById(UUID invoiceId) {
        return jdbc.query("SELECT * FROM invoices WHERE id = ?", ROW_MAPPER, invoiceId)
                .stream().findFirst();
    }

    @Override
    public Optional<Invoice> findByTrip(UUID tripId) {
        return jdbc.query("SELECT * FROM invoices WHERE trip_id = ?", ROW_MAPPER, tripId)
                .stream().findFirst();
    }

    @Override
    public Optional<Invoice> findByTripAndRider(UUID tripId, String riderId) {
        return jdbc.query("SELECT * FROM invoices WHERE trip_id = ? AND rider_id = ?",
                        ROW_MAPPER, tripId, riderId)
                .stream().findFirst();
    }

    @Override
    public void insert(Invoice invoice) {
        jdbc.update("""
                        INSERT INTO invoices (id, trip_id, rider_id, driver_id, amount_cents,
                                              state, issued_at, paid_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                invoice.getId(),
                invoice.getTripId(),
                invoice.getRiderId(),
                invoice.getDriverId(),
                invoice.getAmountCents(),
                invoice.getState().wireValue(),
                Timestamp.from(invoice.getIssuedAt()),
                toTimestamp(invoice.getPaidAt()));
    }

    @Override
    public boolean save(Invoice invoice, InvoiceState expectedState) {
        return jdbc.update("""
                        UPDATE invoices SET state = ?, paid_at = ?
                        WHERE id = ? AND state = ?
                        """,
                invoice.getState().wireValue(),
                toTimestamp(invoice.getPaidAt()),
                invoice.getId(),
                expectedState.wireValue()) > 0;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}

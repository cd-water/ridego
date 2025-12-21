package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.InvoicePayload;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.contracts.TripCompletedPayload;
import com.cdwater.ridego.messaging.OutboxRepository;
import com.cdwater.ridego.messaging.ProcessedEvents;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class InvoiceService {

    private static final String CONSUMER = "billing";
    private static final int ZERO_AMOUNT = 0;

    private final JdbcTemplate jdbc;
    private final OutboxRepository outbox;
    private final ProcessedEvents processedEvents;
    private final Counter invoicesPaid;

    public InvoiceService(JdbcTemplate jdbc,
                          OutboxRepository outbox,
                          ProcessedEvents processedEvents,
                          MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.invoicesPaid = Counter.builder("ridego.invoices.paid").register(meterRegistry);
    }

    @Transactional
    public void onTripCompleted(TripCompletedPayload trip, String eventId) {
        if (!processedEvents.markOnce(CONSUMER, eventId)) {
            return;
        }

        UUID invoiceId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                        INSERT INTO invoices (id, trip_id, rider_id, driver_id, amount_cents, state, issued_at)
                        VALUES (?, ?, ?, ?, ?, 'ISSUED', ?)
                        ON CONFLICT (trip_id) DO NOTHING
                        """,
                invoiceId, UUID.fromString(trip.tripId()), trip.riderId(), trip.driverId(),
                ZERO_AMOUNT, Timestamp.from(now));

        outbox.append(Topics.BILLING_EVENTS, trip.tripId(), null,
                EventTypes.INVOICE_ISSUED,
                new InvoicePayload(invoiceId.toString(), trip.tripId(), trip.riderId(), ZERO_AMOUNT, "ISSUED"));

        jdbc.update("""
                        UPDATE invoices SET state = 'PAID', paid_at = now()
                        WHERE trip_id = ? AND state = 'ISSUED'
                        """,
                UUID.fromString(trip.tripId()));

        outbox.append(Topics.BILLING_EVENTS, trip.tripId(), null,
                EventTypes.INVOICE_PAID,
                new InvoicePayload(invoiceId.toString(), trip.tripId(), trip.riderId(), ZERO_AMOUNT, "PAID"));
        invoicesPaid.increment();
    }

    public Invoice findByTripId(UUID tripId, String riderId) {
        return jdbc.query("""
                        SELECT id, trip_id, rider_id, driver_id, amount_cents, state
                        FROM invoices WHERE trip_id = ?
                        """,
                (rs, rowNum) -> new Invoice(
                        rs.getObject("id", UUID.class),
                        rs.getObject("trip_id", UUID.class),
                        rs.getString("rider_id"),
                        rs.getString("driver_id"),
                        rs.getInt("amount_cents"),
                        rs.getString("state")),
                tripId)
                .stream()
                .filter(invoice -> invoice.riderId().equals(riderId))
                .findFirst()
                .orElseThrow(InvoiceNotFoundException::new);
    }

    public record Invoice(UUID id, UUID tripId, String riderId, String driverId,
                          int amountCents, String state) {
    }
}

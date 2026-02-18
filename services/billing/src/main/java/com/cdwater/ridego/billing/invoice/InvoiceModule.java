package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.contracts.ConflictException;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.InvoicePayload;
import com.cdwater.ridego.contracts.TripCompletedPayload;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

public class InvoiceModule {

    private static final String CONSUMER = "billing";

    private final InvoiceStore store;
    private final InvoiceEventSink events;
    private final DedupStore dedup;
    private final FarePolicy farePolicy;
    private final Clock clock;

    public InvoiceModule(InvoiceStore store,
                         InvoiceEventSink events,
                         DedupStore dedup,
                         FarePolicy farePolicy,
                         Clock clock) {
        this.store = store;
        this.events = events;
        this.dedup = dedup;
        this.farePolicy = farePolicy;
        this.clock = clock;
    }

    @Transactional
    public Invoice issue(TripCompletedPayload trip, String eventId) {
        UUID tripId = UUID.fromString(trip.tripId());
        if (!dedup.markOnce(CONSUMER, eventId)) {
            return store.findByTrip(tripId).orElseThrow(InvoiceNotFoundException::new);
        }

        int amountCents = farePolicy.fareCents(trip);
        Invoice invoice = new Invoice(
                UUID.randomUUID(), tripId, trip.riderId(), trip.driverId(),
                amountCents, InvoiceState.ISSUED, clock.instant(), null);
        store.insert(invoice);
        events.append(EventTypes.INVOICE_ISSUED, tripId,
                new InvoicePayload(invoice.id.toString(), trip.tripId(), trip.riderId(),
                        amountCents, InvoiceState.ISSUED.wireValue()));
        return invoice;
    }

    @Transactional
    public void pay(UUID invoiceId) {
        Invoice invoice = store.findById(invoiceId).orElseThrow(InvoiceNotFoundException::new);
        if (invoice.state == InvoiceState.PAID) {
            return;
        }

        InvoiceState expected = invoice.state;
        invoice.state = InvoiceState.PAID;
        invoice.paidAt = clock.instant();
        if (!store.save(invoice, expected)) {
            throw new ConflictException("invoice changed concurrently");
        }
        events.append(EventTypes.INVOICE_PAID, invoice.tripId,
                new InvoicePayload(invoice.id.toString(), invoice.tripId.toString(),
                        invoice.riderId, invoice.amountCents, InvoiceState.PAID.wireValue()));
    }

    public Optional<Invoice> findForRider(UUID tripId, String riderId) {
        return store.findByTripAndRider(tripId, riderId);
    }
}

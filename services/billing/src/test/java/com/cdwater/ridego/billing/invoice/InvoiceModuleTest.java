package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.contracts.ConflictException;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.InvoicePayload;
import com.cdwater.ridego.contracts.TripCompletedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceModuleTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final UUID TRIP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private InMemoryInvoiceStore store;
    private RecordingSink sink;
    private FakeDedup dedup;
    private InvoiceModule module;

    @BeforeEach
    void setUp() {
        store = new InMemoryInvoiceStore();
        sink = new RecordingSink();
        dedup = new FakeDedup();
        module = new InvoiceModule(store, sink, dedup, trip -> 0,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issueCreatesIssuedInvoiceAndEvent() {
        Invoice invoice = module.issue(trip(), "evt-1");

        assertNotNull(invoice.getId());
        assertEquals(TRIP_ID, invoice.getTripId());
        assertEquals("rider-1", invoice.getRiderId());
        assertEquals("driver-1", invoice.getDriverId());
        assertEquals(0, invoice.getAmountCents());
        assertEquals(InvoiceState.ISSUED, invoice.getState());
        assertEquals(NOW, invoice.getIssuedAt());
        assertNull(invoice.getPaidAt());
        assertTrue(store.findByTrip(TRIP_ID).isPresent());

        assertEquals(1, sink.sent.size());
        Sent sent = sink.sent.getFirst();
        assertEquals(EventTypes.INVOICE_ISSUED, sent.eventType());
        assertEquals(TRIP_ID, sent.tripId());
        InvoicePayload payload = (InvoicePayload) sent.payload();
        assertEquals(invoice.getId().toString(), payload.invoiceId());
        assertEquals(0, payload.amountCents());
        assertEquals("ISSUED", payload.state());
    }

    @Test
    void duplicateEventReturnsExistingInvoiceWithoutNewEvent() {
        Invoice first = module.issue(trip(), "evt-1");
        Invoice second = module.issue(trip(), "evt-1");

        assertEquals(first.getId(), second.getId());
        assertEquals(1, sink.sent.size());
        assertEquals(1, store.byId.size());
    }

    @Test
    void payTransitionsToPaidAndIsIdempotent() {
        Invoice invoice = module.issue(trip(), "evt-1");

        module.pay(invoice.getId());

        assertEquals(InvoiceState.PAID, invoice.getState());
        assertEquals(NOW, invoice.getPaidAt());
        assertEquals(1, sink.of(EventTypes.INVOICE_PAID).size());
        InvoicePayload paidPayload = (InvoicePayload) sink.of(EventTypes.INVOICE_PAID)
                .getFirst().payload();
        assertEquals("PAID", paidPayload.state());

        module.pay(invoice.getId());

        assertEquals(1, sink.of(EventTypes.INVOICE_PAID).size());
    }

    @Test
    void issueWithoutPayStaysIssued() {
        Invoice invoice = module.issue(trip(), "evt-1");

        assertEquals(InvoiceState.ISSUED, invoice.getState());
        assertTrue(sink.of(EventTypes.INVOICE_PAID).isEmpty());
        assertEquals(InvoiceState.ISSUED, store.findByTrip(TRIP_ID).orElseThrow().getState());
    }

    @Test
    void findForRiderRejectsOtherRider() {
        module.issue(trip(), "evt-1");

        assertTrue(module.findForRider(TRIP_ID, "rider-1").isPresent());
        assertTrue(module.findForRider(TRIP_ID, "rider-2").isEmpty());
    }

    @Test
    void farePolicySeamDeterminesAmount() {
        InvoiceModule priced = new InvoiceModule(store, sink, dedup, trip -> 1234,
                Clock.fixed(NOW, ZoneOffset.UTC));

        Invoice invoice = priced.issue(trip(), "evt-1");

        assertEquals(1234, invoice.getAmountCents());
        assertEquals(1234, ((InvoicePayload) sink.sent.getFirst().payload()).amountCents());
    }

    @Test
    void conflictingPayThrowsAndEmitsNoEvent() {
        Invoice invoice = module.issue(trip(), "evt-1");
        store.failNextSave = true;

        assertThrows(ConflictException.class, () -> module.pay(invoice.getId()));

        assertTrue(sink.of(EventTypes.INVOICE_PAID).isEmpty());
    }

    private static TripCompletedPayload trip() {
        return new TripCompletedPayload(
                TRIP_ID.toString(), "rider-1", "driver-1",
                NOW.minusSeconds(600), NOW, 0, 0, 0, 0);
    }

    private record Sent(String eventType, UUID tripId, Object payload) {
    }

    private static final class RecordingSink implements InvoiceEventSink {

        private final List<Sent> sent = new ArrayList<>();

        @Override
        public void append(String eventType, UUID tripId, Object payload) {
            sent.add(new Sent(eventType, tripId, payload));
        }

        private List<Sent> of(String eventType) {
            return sent.stream().filter(s -> s.eventType().equals(eventType)).toList();
        }
    }

    private static final class FakeDedup implements DedupStore {

        private final Set<String> seen = new HashSet<>();

        @Override
        public boolean markOnce(String consumer, String eventId) {
            return seen.add(consumer + ":" + eventId);
        }
    }

    private static final class InMemoryInvoiceStore implements InvoiceStore {

        private final Map<UUID, Invoice> byId = new HashMap<>();
        private final Map<UUID, Invoice> byTrip = new HashMap<>();
        private final Map<UUID, InvoiceState> persistedStates = new HashMap<>();
        private boolean failNextSave;

        @Override
        public Optional<Invoice> findById(UUID invoiceId) {
            return Optional.ofNullable(byId.get(invoiceId));
        }

        @Override
        public Optional<Invoice> findByTrip(UUID tripId) {
            return Optional.ofNullable(byTrip.get(tripId));
        }

        @Override
        public Optional<Invoice> findByTripAndRider(UUID tripId, String riderId) {
            return Optional.ofNullable(byTrip.get(tripId))
                    .filter(invoice -> invoice.getRiderId().equals(riderId));
        }

        @Override
        public void insert(Invoice invoice) {
            byId.put(invoice.getId(), invoice);
            byTrip.put(invoice.getTripId(), invoice);
            persistedStates.put(invoice.getId(), invoice.getState());
        }

        @Override
        public boolean save(Invoice invoice, InvoiceState expectedState) {
            if (failNextSave) {
                failNextSave = false;
                return false;
            }
            if (persistedStates.get(invoice.getId()) != expectedState) {
                return false;
            }
            persistedStates.put(invoice.getId(), invoice.getState());
            return true;
        }
    }
}

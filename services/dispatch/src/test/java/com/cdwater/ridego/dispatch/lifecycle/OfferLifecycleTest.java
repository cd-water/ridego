package com.cdwater.ridego.dispatch.lifecycle;

import com.cdwater.ridego.contracts.EventTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final UUID TRIP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String CITY = "city-1";
    private static final String EVENT_ID = "event-1";
    private static final Duration TTL = Duration.ofSeconds(10);

    private InMemoryOfferStore store;
    private FakeAvailability availability;
    private FakeClaim claim;
    private RecordingSink sink;
    private FakeDedup dedup;
    private MutableClock clock;
    private OfferLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        store = new InMemoryOfferStore();
        availability = new FakeAvailability();
        claim = new FakeClaim();
        sink = new RecordingSink();
        dedup = new FakeDedup();
        clock = new MutableClock(NOW);
        lifecycle = new OfferLifecycle(store, availability, claim, sink, dedup, clock, TTL);
        availability.states.put("driver-1", "IDLE");
        availability.states.put("driver-2", "IDLE");
        availability.states.put("driver-3", "IDLE");
        availability.states.put("driver-4", "IDLE");
    }

    @Test
    void attemptCreatesOfferForFirstIdleCandidate() {
        boolean offered = lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1", "driver-2"));

        assertTrue(offered);
        Offer offer = onlyOffer();
        assertEquals("driver-1", offer.getDriverId());
        assertEquals(OfferState.PENDING, offer.getState());
        assertEquals(NOW.plus(TTL), offer.getExpiresAt());
        assertEquals("OFFERED", availability.states.get("driver-1"));
        assertTrue(claim.claimed.contains("driver-1"));
        assertEquals(List.of(EventTypes.MATCHING_STARTED, EventTypes.OFFER_CREATED), sink.types());
    }

    @Test
    void duplicateEventIdEmitsNothingMore() {
        lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1"));
        lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1"));

        assertEquals(2, sink.events.size());
        assertEquals(1, store.offers.size());
    }

    @Test
    void skipsNonIdleAttemptedAndClaimFailedCandidates() {
        availability.states.put("driver-1", "OFFERED");
        addAttempted("driver-2", OfferState.EXPIRED);
        claim.failNextClaim = true;

        boolean offered = lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID,
                List.of("driver-1", "driver-2", "driver-3", "driver-4"));

        assertTrue(offered);
        assertEquals("driver-4", onlyOffer().getDriverId());
        assertFalse(claim.claimed.contains("driver-3"));
    }

    @Test
    void insertConflictReleasesClaimAndTriesNext() {
        store.failNextInsert = true;

        lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1", "driver-2"));

        assertEquals("driver-2", onlyOffer().getDriverId());
        assertFalse(claim.claimed.contains("driver-1"));
        assertTrue(claim.claimed.contains("driver-2"));
    }

    @Test
    void acceptTransitionsOfferAndDriver() {
        lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1"));
        UUID offerId = onlyOffer().getId();

        lifecycle.accept(offerId, "driver-1");

        assertEquals(OfferState.ACCEPTED, store.offers.get(offerId).getState());
        assertEquals("ON_TRIP", availability.states.get("driver-1"));
        assertFalse(claim.claimed.contains("driver-1"));
        assertEquals(EventTypes.OFFER_ACCEPTED, sink.lastType());
    }

    @Test
    void acceptAfterExpiryConflictsAndEmitsNothing() {
        lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1"));
        UUID offerId = onlyOffer().getId();
        int eventsBefore = sink.events.size();
        clock.advance(Duration.ofSeconds(11));

        assertThrows(OfferConflictException.class, () -> lifecycle.accept(offerId, "driver-1"));
        assertEquals(OfferState.PENDING, store.offers.get(offerId).getState());
        assertEquals(eventsBefore, sink.events.size());
    }

    @Test
    void wrongDriverCannotAccept() {
        lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1"));
        UUID offerId = onlyOffer().getId();

        assertThrows(OfferForbiddenException.class, () -> lifecycle.accept(offerId, "driver-2"));
    }

    @Test
    void rejectReleasesDriverAndIsIdempotent() {
        lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1"));
        UUID offerId = onlyOffer().getId();

        lifecycle.reject(offerId, "driver-1");
        lifecycle.reject(offerId, "driver-1");

        assertEquals(OfferState.REJECTED, store.offers.get(offerId).getState());
        assertEquals("IDLE", availability.states.get("driver-1"));
        assertFalse(claim.claimed.contains("driver-1"));
        assertEquals(1, sink.count(EventTypes.OFFER_REJECTED));
    }

    @Test
    void expireTransitionsOnlyExpiredPendingOffers() {
        lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1"));
        UUID offerId = onlyOffer().getId();

        lifecycle.expire(offerId);
        assertEquals(OfferState.PENDING, store.offers.get(offerId).getState());

        clock.advance(Duration.ofSeconds(11));
        lifecycle.expire(offerId);
        lifecycle.expire(offerId);

        assertEquals(OfferState.EXPIRED, store.offers.get(offerId).getState());
        assertEquals("IDLE", availability.states.get("driver-1"));
        assertFalse(claim.claimed.contains("driver-1"));
        assertEquals(1, sink.count(EventTypes.OFFER_EXPIRED));
    }

    @Test
    void closeForTripReleasesWithoutEvents() {
        lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1"));
        UUID offerId = onlyOffer().getId();
        lifecycle.accept(offerId, "driver-1");
        int eventsBefore = sink.events.size();

        lifecycle.closeForTrip(TRIP_ID);

        assertEquals(OfferState.RELEASED, store.offers.get(offerId).getState());
        assertEquals("IDLE", availability.states.get("driver-1"));
        assertFalse(claim.claimed.contains("driver-1"));
        assertEquals(eventsBefore, sink.events.size());
    }

    @Test
    void releaseAcceptedAndReleaseDriverAreSilent() {
        lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1"));
        UUID offerId = onlyOffer().getId();
        lifecycle.accept(offerId, "driver-1");
        int eventsBefore = sink.events.size();

        lifecycle.releaseAccepted(offerId, "driver-1");

        assertEquals(OfferState.RELEASED, store.offers.get(offerId).getState());
        assertEquals(eventsBefore, sink.events.size());
    }

    @Test
    void markOfferedFailureEmitsNoOfferCreated() {
        availability.failNextMarkOffered = true;

        assertThrows(OfferConflictException.class,
                () -> lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1")));
        assertEquals(List.of(EventTypes.MATCHING_STARTED), sink.types());
        assertTrue(claim.claimed.isEmpty(), "claim must be released when markOffered fails");
    }

    @Test
    void markOnTripFailureEmitsNoAccepted() {
        lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1"));
        UUID offerId = onlyOffer().getId();
        availability.failNextMarkOnTrip = true;

        assertThrows(OfferConflictException.class, () -> lifecycle.accept(offerId, "driver-1"));
        assertEquals(0, sink.count(EventTypes.OFFER_ACCEPTED));
        assertTrue(claim.claimed.isEmpty(), "claim must be released when markOnTrip fails");
    }

    @Test
    void releaseFailureStillExpiresOffer() {
        lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1"));
        UUID offerId = onlyOffer().getId();
        clock.advance(Duration.ofSeconds(11));
        availability.failNextRelease = true;

        lifecycle.expire(offerId);

        assertEquals(OfferState.EXPIRED, store.offers.get(offerId).getState());
        assertEquals(1, sink.count(EventTypes.OFFER_EXPIRED));
    }

    @Test
    void casFailureEmitsNoEvents() {
        lifecycle.attemptOffer(TRIP_ID, CITY, EVENT_ID, List.of("driver-1"));
        UUID offerId = onlyOffer().getId();
        int eventsBefore = sink.events.size();
        store.failNextSave = true;

        assertThrows(OfferConflictException.class, () -> lifecycle.accept(offerId, "driver-1"));
        assertEquals(eventsBefore, sink.events.size());
    }

    private Offer onlyOffer() {
        return store.offers.values().stream()
                .filter(offer -> offer.state == OfferState.PENDING)
                .findFirst()
                .orElseThrow();
    }

    private void addAttempted(String driverId, OfferState state) {
        Offer offer = new Offer(UUID.randomUUID(), TRIP_ID, driverId, CITY,
                state, NOW, NOW.plus(TTL), NOW);
        store.offers.put(offer.id, offer);
        store.persistedStates.put(offer.id, state);
    }

    static final class InMemoryOfferStore implements OfferStore {
        final Map<UUID, Offer> offers = new HashMap<>();
        final Map<UUID, OfferState> persistedStates = new HashMap<>();
        boolean failNextInsert;
        boolean failNextSave;

        @Override
        public Optional<Offer> load(UUID offerId) {
            return Optional.ofNullable(offers.get(offerId));
        }

        @Override
        public List<Offer> pendingForDriver(String driverId) {
            return offers.values().stream()
                    .filter(o -> o.driverId.equals(driverId) && o.state == OfferState.PENDING)
                    .toList();
        }

        @Override
        public List<Offer> findExpired(Instant now, int limit) {
            return offers.values().stream()
                    .filter(o -> o.state == OfferState.PENDING && !o.expiresAt.isAfter(now))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Offer> findActiveForTrip(UUID tripId) {
            return offers.values().stream()
                    .filter(o -> o.tripId.equals(tripId)
                            && (o.state == OfferState.PENDING || o.state == OfferState.ACCEPTED))
                    .toList();
        }

        @Override
        public Set<String> attemptedDrivers(UUID tripId) {
            Set<String> drivers = new HashSet<>();
            offers.values().stream()
                    .filter(o -> o.tripId.equals(tripId))
                    .filter(o -> o.state == OfferState.EXPIRED
                            || o.state == OfferState.REJECTED
                            || o.state == OfferState.RELEASED)
                    .forEach(o -> drivers.add(o.driverId));
            return drivers;
        }

        @Override
        public boolean insert(Offer offer) {
            if (failNextInsert) {
                failNextInsert = false;
                return false;
            }
            offers.put(offer.id, offer);
            persistedStates.put(offer.id, offer.state);
            return true;
        }

        @Override
        public boolean save(Offer offer, String expectedState) {
            if (failNextSave) {
                failNextSave = false;
                return false;
            }
            OfferState current = persistedStates.get(offer.id);
            if (current == null || !current.wireValue().equals(expectedState)) {
                return false;
            }
            persistedStates.put(offer.id, offer.state);
            return true;
        }
    }

    static final class FakeAvailability implements DriverAvailability {
        final Map<String, String> states = new HashMap<>();
        boolean failNextMarkOffered;
        boolean failNextMarkOnTrip;
        boolean failNextRelease;

        @Override
        public boolean isIdle(String driverId, String cityId) {
            return "IDLE".equals(states.get(driverId));
        }

        @Override
        public boolean markOffered(String driverId) {
            if (failNextMarkOffered) {
                failNextMarkOffered = false;
                return false;
            }
            states.put(driverId, "OFFERED");
            return true;
        }

        @Override
        public boolean markOnTrip(String driverId) {
            if (failNextMarkOnTrip) {
                failNextMarkOnTrip = false;
                return false;
            }
            states.put(driverId, "ON_TRIP");
            return true;
        }

        @Override
        public boolean release(String driverId) {
            if (failNextRelease) {
                failNextRelease = false;
                return false;
            }
            states.put(driverId, "IDLE");
            return true;
        }

        @Override
        public boolean markOnline(String driverId, String cityId) {
            states.put(driverId, "IDLE");
            return true;
        }

        @Override
        public boolean markOffline(String driverId, String cityId) {
            states.put(driverId, "OFFLINE");
            return true;
        }
    }

    static final class FakeClaim implements OfferClaim {
        final Set<String> claimed = new HashSet<>();
        boolean failNextClaim;

        @Override
        public boolean tryClaim(String driverId, Duration ttl) {
            if (failNextClaim) {
                failNextClaim = false;
                return false;
            }
            return claimed.add(driverId);
        }

        @Override
        public void release(String driverId) {
            claimed.remove(driverId);
        }
    }

    static final class RecordingSink implements DispatchEventSink {
        final List<Sent> events = new ArrayList<>();

        @Override
        public void append(String eventType, UUID tripId, String cityId, Object payload) {
            events.add(new Sent(eventType, tripId, payload));
        }

        List<String> types() {
            return events.stream().map(Sent::type).toList();
        }

        String lastType() {
            return events.get(events.size() - 1).type();
        }

        long count(String type) {
            return events.stream().filter(e -> e.type().equals(type)).count();
        }
    }

    record Sent(String type, UUID tripId, Object payload) {
    }

    static final class FakeDedup implements DedupStore {
        final Set<String> seen = new HashSet<>();

        @Override
        public boolean markOnce(String consumer, String eventId) {
            return seen.add(consumer + ":" + eventId);
        }
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

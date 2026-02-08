package com.cdwater.ridego.dispatch.lifecycle;

import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.contracts.TripLifecyclePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class OfferLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OfferLifecycle.class);
    private static final String MATCHING_CONSUMER = "dispatch-matching";

    private final OfferStore store;
    private final DriverAvailability availability;
    private final OfferClaim claim;
    private final DispatchEventSink events;
    private final DedupStore dedup;
    private final Clock clock;
    private final Duration offerTtl;

    public OfferLifecycle(OfferStore store,
                          DriverAvailability availability,
                          OfferClaim claim,
                          DispatchEventSink events,
                          DedupStore dedup,
                          Clock clock,
                          Duration offerTtl) {
        this.store = store;
        this.availability = availability;
        this.claim = claim;
        this.events = events;
        this.dedup = dedup;
        this.clock = clock;
        this.offerTtl = offerTtl;
    }

    @Transactional
    public boolean attemptOffer(UUID tripId, String cityId, String eventId,
                                List<String> candidateDriverIds) {
        if (!dedup.markOnce(MATCHING_CONSUMER, eventId)) {
            return true;
        }

        Instant now = clock.instant();
        events.append(EventTypes.MATCHING_STARTED, tripId, cityId,
                new TripLifecyclePayload(tripId.toString()));

        Set<String> attempted = store.attemptedDrivers(tripId);
        for (String driverId : candidateDriverIds) {
            if (attempted.contains(driverId)) {
                continue;
            }
            if (!availability.isIdle(driverId, cityId)) {
                continue;
            }
            if (!claim.tryClaim(driverId, offerTtl)) {
                continue;
            }

            Offer offer = new Offer(UUID.randomUUID(), tripId, driverId, cityId,
                    OfferState.PENDING, now, now.plus(offerTtl), null);
            if (!store.insert(offer)) {
                claim.release(driverId);
                continue;
            }
            if (!availability.markOffered(driverId)) {
                claim.release(driverId);
                throw new OfferConflictException(
                        "driver " + driverId + " is no longer available");
            }

            events.append(EventTypes.OFFER_CREATED, tripId, cityId, payload(offer, driverId));
            return true;
        }
        return false;
    }

    @Transactional
    public void accept(UUID offerId, String driverId) {
        Offer offer = load(offerId);
        if (!offer.driverId.equals(driverId)) {
            throw new OfferForbiddenException();
        }
        if (offer.state == OfferState.ACCEPTED) {
            return;
        }
        if (offer.state != OfferState.PENDING) {
            throw conflict(offer, OfferState.ACCEPTED);
        }
        Instant now = clock.instant();
        if (!offer.expiresAt.isAfter(now)) {
            throw new OfferConflictException("offer expired");
        }

        transition(offer, OfferState.ACCEPTED, now);
        if (!availability.markOnTrip(driverId)) {
            claim.release(driverId);
            throw new OfferConflictException(
                    "driver " + driverId + " is no longer available");
        }
        claim.release(driverId);
        events.append(EventTypes.OFFER_ACCEPTED, offer.tripId, offer.cityId, payload(offer, driverId));
    }

    @Transactional
    public void reject(UUID offerId, String driverId) {
        Offer offer = load(offerId);
        if (!offer.driverId.equals(driverId)) {
            throw new OfferForbiddenException();
        }
        if (offer.state == OfferState.REJECTED) {
            return;
        }
        if (offer.state != OfferState.PENDING) {
            throw conflict(offer, OfferState.REJECTED);
        }

        transition(offer, OfferState.REJECTED, clock.instant());
        if (!availability.release(driverId)) {
            log.warn("availability release lost for driver={} after reject", driverId);
        }
        claim.release(driverId);
        events.append(EventTypes.OFFER_REJECTED, offer.tripId, offer.cityId, payload(offer, driverId));
    }

    @Transactional
    public void expire(UUID offerId) {
        Offer offer = store.load(offerId).orElse(null);
        if (offer == null || offer.state != OfferState.PENDING) {
            return;
        }
        Instant now = clock.instant();
        if (offer.expiresAt.isAfter(now)) {
            return;
        }

        transition(offer, OfferState.EXPIRED, now);
        if (!availability.release(offer.driverId)) {
            log.warn("availability release lost for driver={} after expiry", offer.driverId);
        }
        claim.release(offer.driverId);
        events.append(EventTypes.OFFER_EXPIRED, offer.tripId, offer.cityId,
                payload(offer, offer.driverId));
    }

    @Transactional
    public void closeForTrip(UUID tripId) {
        for (Offer offer : store.findActiveForTrip(tripId)) {
            release(offer);
        }
    }

    @Transactional
    public void releaseAccepted(UUID offerId, String driverId) {
        Offer offer = store.load(offerId).orElse(null);
        if (offer == null || offer.state != OfferState.ACCEPTED) {
            return;
        }
        if (!offer.driverId.equals(driverId)) {
            return;
        }
        release(offer);
    }

    @Transactional
    public void releaseDriver(UUID tripId, String driverId) {
        for (Offer offer : store.findActiveForTrip(tripId)) {
            if (offer.driverId.equals(driverId)) {
                release(offer);
            }
        }
    }

    private void release(Offer offer) {
        OfferState expected = offer.state;
        offer.state = OfferState.RELEASED;
        offer.respondedAt = clock.instant();
        if (!store.save(offer, expected.wireValue())) {
            return;
        }
        if (!availability.release(offer.driverId)) {
            log.warn("availability release lost for driver={} after offer release",
                    offer.driverId);
        }
        claim.release(offer.driverId);
    }

    private void transition(Offer offer, OfferState next, Instant now) {
        OfferState expected = offer.state;
        offer.state = next;
        offer.respondedAt = now;
        if (!store.save(offer, expected.wireValue())) {
            throw new OfferConflictException("offer changed concurrently");
        }
    }

    private Offer load(UUID offerId) {
        return store.load(offerId).orElseThrow(OfferNotFoundException::new);
    }

    private OfferLifecyclePayload payload(Offer offer, String driverId) {
        return new OfferLifecyclePayload(
                offer.id.toString(), offer.tripId.toString(), driverId, offer.expiresAt);
    }

    private OfferConflictException conflict(Offer offer, OfferState target) {
        return new OfferConflictException(
                "offer " + offer.id + " in state " + offer.state.wireValue()
                        + " cannot move to " + target.wireValue());
    }
}

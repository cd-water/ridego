package com.cdwater.ridego.dispatch.offer;

import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.dispatch.availability.AvailabilityRepository;
import com.cdwater.ridego.dispatch.matching.MatchingTransaction;
import com.cdwater.ridego.dispatch.outbox.DispatchEvents;
import com.cdwater.ridego.dispatch.web.ConflictException;
import com.cdwater.ridego.dispatch.web.ForbiddenException;
import com.cdwater.ridego.dispatch.web.NotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OfferService {

    private final OfferRepository offers;
    private final AvailabilityRepository availability;
    private final DispatchEvents dispatchEvents;
    private final StringRedisTemplate redis;
    private final Counter offersAccepted;
    private final Counter offersRejected;

    public OfferService(OfferRepository offers,
                        AvailabilityRepository availability,
                        DispatchEvents dispatchEvents,
                        StringRedisTemplate redis,
                        MeterRegistry meterRegistry) {
        this.offers = offers;
        this.availability = availability;
        this.dispatchEvents = dispatchEvents;
        this.redis = redis;
        this.offersAccepted = Counter.builder("ridego.offers.accepted").register(meterRegistry);
        this.offersRejected = Counter.builder("ridego.offers.rejected").register(meterRegistry);
    }

    public List<Offer> pendingForDriver(String driverId) {
        return offers.pendingForDriver(driverId, Instant.now());
    }

    @Transactional
    public void accept(UUID offerId, String driverId) {
        Offer offer = requireOwnOffer(offerId, driverId);
        if (offers.accept(offerId) == 0) {
            throw new ConflictException("offer is no longer pending");
        }
        availability.move(driverId, "OFFERED", "ON_TRIP");
        dispatchEvents.append(EventTypes.OFFER_ACCEPTED, offer.tripId(), offer.cityId(),
                new OfferLifecyclePayload(offerId.toString(), offer.tripId().toString(), driverId, offer.expiresAt()));
        redis.delete(MatchingTransaction.claimKey(driverId));
        offersAccepted.increment();
    }

    @Transactional
    public void reject(UUID offerId, String driverId) {
        Offer offer = requireOwnOffer(offerId, driverId);
        if (offers.reject(offerId) == 0) {
            throw new ConflictException("offer is no longer pending");
        }
        availability.move(driverId, "OFFERED", "IDLE");
        dispatchEvents.append(EventTypes.OFFER_REJECTED, offer.tripId(), offer.cityId(),
                new OfferLifecyclePayload(offerId.toString(), offer.tripId().toString(), driverId, offer.expiresAt()));
        redis.delete(MatchingTransaction.claimKey(driverId));
        offersRejected.increment();
    }

    private Offer requireOwnOffer(UUID offerId, String driverId) {
        Offer offer = offers.find(offerId).orElseThrow(NotFoundException::new);
        if (!offer.driverId().equals(driverId)) {
            throw new ForbiddenException();
        }
        return offer;
    }
}

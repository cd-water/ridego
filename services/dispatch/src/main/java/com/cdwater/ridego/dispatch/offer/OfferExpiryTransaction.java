package com.cdwater.ridego.dispatch.offer;

import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.dispatch.availability.AvailabilityRepository;
import com.cdwater.ridego.dispatch.matching.MatchingTransaction;
import com.cdwater.ridego.dispatch.outbox.DispatchEvents;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferExpiryTransaction {

    private final OfferRepository offers;
    private final AvailabilityRepository availability;
    private final DispatchEvents dispatchEvents;
    private final StringRedisTemplate redis;
    private final Counter offersExpired;

    public OfferExpiryTransaction(OfferRepository offers,
                                  AvailabilityRepository availability,
                                  DispatchEvents dispatchEvents,
                                  StringRedisTemplate redis,
                                  MeterRegistry meterRegistry) {
        this.offers = offers;
        this.availability = availability;
        this.dispatchEvents = dispatchEvents;
        this.redis = redis;
        this.offersExpired = Counter.builder("ridego.offers.expired").register(meterRegistry);
    }

    @Transactional
    public void expire(Offer offer) {
        if (offers.expire(offer.id()) == 0) {
            return;
        }
        availability.release(offer.driverId());
        dispatchEvents.append(EventTypes.OFFER_EXPIRED, offer.tripId(), offer.cityId(),
                new OfferLifecyclePayload(offer.id().toString(), offer.tripId().toString(),
                        offer.driverId(), offer.expiresAt()));
        redis.delete(MatchingTransaction.claimKey(offer.driverId()));
        offersExpired.increment();
    }
}

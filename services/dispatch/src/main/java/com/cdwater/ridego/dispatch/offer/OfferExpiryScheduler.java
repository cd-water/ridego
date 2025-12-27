package com.cdwater.ridego.dispatch.offer;

import com.cdwater.ridego.dispatch.lifecycle.Offer;
import com.cdwater.ridego.dispatch.lifecycle.OfferLifecycle;
import com.cdwater.ridego.dispatch.lifecycle.OfferNotFoundException;
import com.cdwater.ridego.dispatch.lifecycle.OfferStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class OfferExpiryScheduler {

    private final OfferStore offerStore;
    private final OfferLifecycle offerLifecycle;
    private final Clock clock;

    public OfferExpiryScheduler(OfferStore offerStore,
                                OfferLifecycle offerLifecycle,
                                Clock clock) {
        this.offerStore = offerStore;
        this.offerLifecycle = offerLifecycle;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${ridego.matching.expiry-scan-ms:1000}")
    public void expireOffers() {
        for (Offer offer : offerStore.findExpired(clock.instant(), 100)) {
            try {
                offerLifecycle.expire(offer.getId());
            } catch (OfferNotFoundException ignored) {
                // offer disappeared between query and intent
            }
        }
    }
}

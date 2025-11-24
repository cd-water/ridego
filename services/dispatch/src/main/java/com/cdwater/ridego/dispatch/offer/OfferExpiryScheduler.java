package com.cdwater.ridego.dispatch.offer;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OfferExpiryScheduler {

    private final OfferRepository offers;
    private final OfferExpiryTransaction expiryTransaction;

    public OfferExpiryScheduler(OfferRepository offers, OfferExpiryTransaction expiryTransaction) {
        this.offers = offers;
        this.expiryTransaction = expiryTransaction;
    }

    @Scheduled(fixedDelayString = "${ridego.matching.expiry-scan-ms:1000}")
    public void expireOffers() {
        for (Offer offer : offers.findExpired(Instant.now(), 100)) {
            expiryTransaction.expire(offer);
        }
    }
}

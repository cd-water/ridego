package com.cdwater.ridego.dispatch.tripclosure;

import com.cdwater.ridego.dispatch.availability.AvailabilityRepository;
import com.cdwater.ridego.dispatch.matching.MatchingTransaction;
import com.cdwater.ridego.dispatch.offer.Offer;
import com.cdwater.ridego.dispatch.offer.OfferRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class TripClosureTransaction {

    private final OfferRepository offers;
    private final AvailabilityRepository availability;
    private final StringRedisTemplate redis;

    public TripClosureTransaction(OfferRepository offers,
                                  AvailabilityRepository availability,
                                  StringRedisTemplate redis) {
        this.offers = offers;
        this.availability = availability;
        this.redis = redis;
    }

    @Transactional
    public void close(UUID tripId, String completedDriverId) {
        Set<String> drivers = new HashSet<>();
        if (completedDriverId != null) {
            drivers.add(completedDriverId);
        }
        for (Offer offer : offers.findActiveForTrip(tripId)) {
            if ("PENDING".equals(offer.state())) {
                offers.expire(offer.id());
            }
            drivers.add(offer.driverId());
        }

        for (String driverId : drivers) {
            availability.release(driverId);
            redis.delete(MatchingTransaction.claimKey(driverId));
        }
    }
}

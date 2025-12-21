package com.cdwater.ridego.dispatch.matching;

import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.contracts.TripRequestedPayload;
import com.cdwater.ridego.dispatch.availability.AvailabilityRepository;
import com.cdwater.ridego.dispatch.client.LocationClient;
import com.cdwater.ridego.dispatch.offer.OfferRepository;
import com.cdwater.ridego.dispatch.outbox.DispatchEvents;
import com.cdwater.ridego.messaging.ProcessedEvents;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MatchingTransaction {

    private static final Logger log = LoggerFactory.getLogger(MatchingTransaction.class);
    private static final String CONSUMER = "dispatch-matching";

    private final ProcessedEvents processedEvents;
    private final AvailabilityRepository availability;
    private final OfferRepository offers;
    private final DispatchEvents dispatchEvents;
    private final StringRedisTemplate redis;
    private final Duration claimTtl;
    private final Counter offersCreated;

    public MatchingTransaction(ProcessedEvents processedEvents,
                               AvailabilityRepository availability,
                               OfferRepository offers,
                               DispatchEvents dispatchEvents,
                               StringRedisTemplate redis,
                               @Value("${ridego.matching.offer-ttl-seconds:10}") long offerTtlSeconds,
                               MeterRegistry meterRegistry) {
        this.processedEvents = processedEvents;
        this.availability = availability;
        this.offers = offers;
        this.dispatchEvents = dispatchEvents;
        this.redis = redis;
        this.claimTtl = Duration.ofSeconds(offerTtlSeconds);
        this.offersCreated = Counter.builder("ridego.offers.issued").register(meterRegistry);
    }

    @Transactional
    public boolean apply(TripRequestedPayload trip, String eventId, List<LocationClient.NearbyDriver> candidates) {
        if (!processedEvents.markOnce(CONSUMER, eventId)) {
            return true;
        }

        UUID tripId = UUID.fromString(trip.tripId());
        Instant now = Instant.now();
        Instant expiresAt = now.plus(claimTtl);
        var excluded = offers.attemptedDrivers(tripId);

        for (LocationClient.NearbyDriver candidate : candidates) {
            String driverId = candidate.driverId();
            if (excluded.contains(driverId)) {
                continue;
            }
            if (!availability.isIdle(driverId, trip.cityId())) {
                continue;
            }
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(claimKey(driverId), "1", claimTtl))) {
                continue;
            }

            UUID offerId = UUID.randomUUID();
            try {
                offers.insert(offerId, tripId, driverId, trip.cityId(), now, expiresAt);
            } catch (DuplicateKeyException e) {
                log.debug("driver {} already has a pending offer, trying next", driverId);
                redis.delete(claimKey(driverId));
                continue;
            }

            availability.move(driverId, "IDLE", "OFFERED");
            dispatchEvents.append(EventTypes.OFFER_CREATED, tripId, trip.cityId(),
                    new OfferLifecyclePayload(offerId.toString(), trip.tripId(), driverId, expiresAt));
            offersCreated.increment();
            return true;
        }
        return false;
    }

    public static String claimKey(String driverId) {
        return "dispatch:claim:" + driverId;
    }
}

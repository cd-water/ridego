package com.cdwater.ridego.dispatch.matching;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.TripRequestedPayload;
import com.cdwater.ridego.dispatch.client.LocationClient;
import com.cdwater.ridego.dispatch.lifecycle.OfferLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final LocationClient locationClient;
    private final OfferLifecycle offerLifecycle;
    private final ObjectMapper objectMapper;
    private final double radiusKm;
    private final int candidateLimit;

    public MatchingService(LocationClient locationClient,
                           OfferLifecycle offerLifecycle,
                           ObjectMapper objectMapper,
                           @Value("${ridego.matching.radius-km:3}") double radiusKm,
                           @Value("${ridego.matching.candidate-limit:50}") int candidateLimit) {
        this.locationClient = locationClient;
        this.offerLifecycle = offerLifecycle;
        this.objectMapper = objectMapper;
        this.radiusKm = radiusKm;
        this.candidateLimit = candidateLimit;
    }

    public void onTripRequested(EventEnvelope<tools.jackson.databind.JsonNode> event) {
        TripRequestedPayload trip = objectMapper.treeToValue(event.payload(), TripRequestedPayload.class);
        UUID tripId = UUID.fromString(trip.tripId());

        List<LocationClient.NearbyDriver> candidates;
        try {
            candidates = locationClient.nearby(
                    trip.cityId(), trip.pickupLat(), trip.pickupLng(), radiusKm, candidateLimit);
        } catch (Exception e) {
            log.warn("nearby query failed for trip={}, will retry via redelivery", trip.tripId(), e);
            throw e;
        }

        List<String> driverIds = candidates.stream()
                .map(LocationClient.NearbyDriver::driverId)
                .toList();
        boolean offered = offerLifecycle.attemptOffer(tripId, trip.cityId(), event.eventId(), driverIds);
        if (!offered) {
            log.info("no idle driver found for trip={} candidates={}", trip.tripId(), driverIds.size());
        }
    }
}

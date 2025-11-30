package com.cdwater.ridego.trip.trip;

import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.contracts.TripDriverPayload;
import com.cdwater.ridego.contracts.TripLifecyclePayload;
import com.cdwater.ridego.contracts.TripOfferedPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TripTransitionService {

    private final TripRepository trips;
    private final TripEvents events;
    private final long matchingWindowSeconds;

    public TripTransitionService(TripRepository trips,
                                 TripEvents events,
                                 @Value("${ridego.matching.window-seconds:90}") long matchingWindowSeconds) {
        this.trips = trips;
        this.events = events;
        this.matchingWindowSeconds = matchingWindowSeconds;
    }

    @Transactional
    public void matchingStarted(String tripId) {
        TripRecord trip = find(tripId);
        if (trip == null || !"requested".equals(trip.state())) {
            return;
        }
        if (trips.updateToMatching(trip.id(), "requested") == 0) {
            return;
        }
        events.append(EventTypes.TRIP_MATCHING_STARTED, trip, new TripLifecyclePayload(tripId));
    }

    @Transactional
    public void offerCreated(OfferLifecyclePayload payload) {
        TripRecord trip = find(payload.tripId());
        if (trip == null || !"matching".equals(trip.state())) {
            return;
        }
        if (trips.updateToOffered(trip.id(), "matching",
                UUID.fromString(payload.offerId()), payload.driverId()) == 0) {
            return;
        }
        events.append(EventTypes.TRIP_OFFERED, trip,
                new TripOfferedPayload(payload.tripId(), payload.driverId(), payload.offerId()));
    }

    @Transactional
    public void offerClosed(OfferLifecyclePayload payload) {
        TripRecord trip = find(payload.tripId());
        if (trip == null || !"offered".equals(trip.state())) {
            return;
        }
        Instant deadline = Instant.now().plusSeconds(matchingWindowSeconds);
        if (trips.updateToMatchingForRedispatch(trip.id(), "offered", deadline) == 0) {
            return;
        }
        events.append(EventTypes.TRIP_MATCHING_RESTARTED, trip, events.requestedPayload(trip));
    }

    @Transactional
    public void offerAccepted(OfferLifecyclePayload payload) {
        TripRecord trip = find(payload.tripId());
        if (trip == null) {
            return;
        }
        if ("offered".equals(trip.state())) {
            if (trips.updateToAccepted(trip.id(), "offered") == 0) {
                return;
            }
            events.append(EventTypes.TRIP_ACCEPTED, trip,
                    new TripDriverPayload(payload.tripId(), payload.driverId()));
            return;
        }
        if ("accepted".equals(trip.state())) {
            return;
        }
        events.append(EventTypes.TRIP_ACCEPT_REJECTED, trip, payload);
    }

    private TripRecord find(String tripId) {
        return trips.find(UUID.fromString(tripId)).orElse(null);
    }
}

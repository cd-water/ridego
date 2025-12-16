package com.cdwater.ridego.trip.lifecycle;

import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.OfferLifecyclePayload;
import com.cdwater.ridego.contracts.TripCancelledPayload;
import com.cdwater.ridego.contracts.TripCompletedPayload;
import com.cdwater.ridego.contracts.TripDriverCancelledPayload;
import com.cdwater.ridego.contracts.TripDriverPayload;
import com.cdwater.ridego.contracts.TripLifecyclePayload;
import com.cdwater.ridego.contracts.TripOfferedPayload;
import com.cdwater.ridego.contracts.TripRequestedPayload;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class TripLifecycle {

    private final TripStore store;
    private final TripEventSink sink;
    private final Clock clock;
    private final long matchingWindowSeconds;

    public TripLifecycle(TripStore store, TripEventSink sink, Clock clock, long matchingWindowSeconds) {
        this.store = store;
        this.sink = sink;
        this.clock = clock;
        this.matchingWindowSeconds = matchingWindowSeconds;
    }

    @Transactional
    public UUID request(String riderId, String cityId,
                        double pickupLat, double pickupLng,
                        double dropoffLat, double dropoffLng) {
        if (cityId == null || cityId.isBlank()) {
            throw new TripBadRequestException("cityId is required");
        }
        if (!validLat(pickupLat) || !validLat(dropoffLat)
                || !validLng(pickupLng) || !validLng(dropoffLng)) {
            throw new TripBadRequestException("invalid coordinates");
        }

        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        Trip trip = Trip.requested(id, cityId, riderId,
                pickupLat, pickupLng, dropoffLat, dropoffLng,
                now, now.plusSeconds(matchingWindowSeconds));
        store.insert(trip);
        emit(EventTypes.TRIP_REQUESTED, trip,
                new TripRequestedPayload(id.toString(), riderId, cityId,
                        pickupLat, pickupLng, dropoffLat, dropoffLng), now);
        return id;
    }

    @Transactional
    public void beginMatching(UUID tripId) {
        Trip trip = load(tripId);
        if (trip.state == TripState.MATCHING) {
            return;
        }
        if (trip.state != TripState.REQUESTED) {
            throw conflict(trip, TripState.MATCHING);
        }
        Instant now = clock.instant();
        TripState expected = trip.state;
        trip.state = TripState.MATCHING;
        trip.matchingStartedAt = now;
        persist(trip, expected);
        emit(EventTypes.TRIP_MATCHING_STARTED, trip,
                new TripLifecyclePayload(tripId.toString()), now);
    }

    @Transactional
    public void offerTo(UUID tripId, UUID offerId, String driverId) {
        Trip trip = load(tripId);
        if (trip.state == TripState.OFFERED
                && offerId.toString().equals(trip.offerId)
                && driverId.equals(trip.driverId)) {
            return;
        }
        if (trip.state != TripState.MATCHING) {
            throw conflict(trip, TripState.OFFERED);
        }
        Instant now = clock.instant();
        TripState expected = trip.state;
        trip.state = TripState.OFFERED;
        trip.offerId = offerId.toString();
        trip.driverId = driverId;
        persist(trip, expected);
        emit(EventTypes.TRIP_OFFERED, trip,
                new TripOfferedPayload(tripId.toString(), driverId, offerId.toString()), now);
    }

    @Transactional
    public void acceptOffer(UUID tripId, UUID offerId, String driverId) {
        Trip trip = load(tripId);
        if (trip.state == TripState.ACCEPTED
                && offerId.toString().equals(trip.offerId)
                && driverId.equals(trip.driverId)) {
            return;
        }
        if (trip.state == TripState.OFFERED
                && offerId.toString().equals(trip.offerId)
                && driverId.equals(trip.driverId)) {
            Instant now = clock.instant();
            TripState expected = trip.state;
            trip.state = TripState.ACCEPTED;
            trip.acceptedAt = now;
            persist(trip, expected);
            emit(EventTypes.TRIP_ACCEPTED, trip,
                    new TripDriverPayload(tripId.toString(), driverId), now);
            return;
        }

        emit(EventTypes.TRIP_ACCEPT_REJECTED, trip,
                new OfferLifecyclePayload(offerId.toString(), tripId.toString(), driverId, null),
                clock.instant());
    }

    @Transactional
    public void offerFailed(UUID tripId, UUID offerId, String driverId) {
        Trip trip = load(tripId);
        if (trip.state != TripState.OFFERED || !offerId.toString().equals(trip.offerId)) {
            return;
        }
        Instant now = clock.instant();
        TripState expected = trip.state;
        trip.state = TripState.MATCHING;
        trip.offerId = null;
        trip.driverId = null;
        trip.matchingDeadline = now.plusSeconds(matchingWindowSeconds);
        persist(trip, expected);
        emit(EventTypes.TRIP_MATCHING_RESTARTED, trip,
                requestedPayload(trip), now);
    }

    @Transactional
    public void cancel(UUID tripId, ActorType actorType, String actorId, String reason) {
        Trip trip = load(tripId);
        String cancelReason = reason == null || reason.isBlank() ? "user_requested" : reason;
        if (actorType == ActorType.RIDER) {
            cancelByRider(trip, actorId, cancelReason);
        } else {
            cancelByDriver(trip, actorId, cancelReason);
        }
    }

    private void cancelByRider(Trip trip, String riderId, String reason) {
        if (!riderId.equals(trip.riderId)) {
            throw new TripForbiddenException();
        }
        if (trip.state == TripState.CANCELLED && "rider".equals(trip.cancelActor)) {
            return;
        }
        if (!trip.state.canTransitionTo(TripState.CANCELLED)) {
            throw conflict(trip, TripState.CANCELLED);
        }
        Instant now = clock.instant();
        TripState expected = trip.state;
        trip.state = TripState.CANCELLED;
        trip.cancelledAt = now;
        trip.cancelActor = "rider";
        trip.cancelReason = reason;
        persist(trip, expected);
        emit(EventTypes.TRIP_CANCELLED, trip,
                new TripCancelledPayload(trip.id.toString(), "rider", reason), now);
    }

    private void cancelByDriver(Trip trip, String driverId, String reason) {
        if (trip.driverId == null || !driverId.equals(trip.driverId)) {
            throw new TripForbiddenException();
        }
        if (trip.state != TripState.ACCEPTED && trip.state != TripState.ARRIVED) {
            throw conflict(trip, TripState.MATCHING);
        }
        Instant now = clock.instant();
        TripState expected = trip.state;
        trip.state = TripState.MATCHING;
        trip.offerId = null;
        trip.driverId = null;
        trip.matchingDeadline = now.plusSeconds(matchingWindowSeconds);
        persist(trip, expected);
        emit(EventTypes.TRIP_DRIVER_CANCELLED, trip,
                new TripDriverCancelledPayload(trip.id.toString(), driverId, reason), now);
        emit(EventTypes.TRIP_MATCHING_RESTARTED, trip, requestedPayload(trip), now);
    }

    @Transactional
    public void arrive(UUID tripId, String driverId) {
        Trip trip = load(tripId);
        requireDriver(trip, driverId);
        if (trip.state == TripState.ARRIVED) {
            return;
        }
        if (trip.state != TripState.ACCEPTED) {
            throw conflict(trip, TripState.ARRIVED);
        }
        Instant now = clock.instant();
        TripState expected = trip.state;
        trip.state = TripState.ARRIVED;
        trip.arrivedAt = now;
        persist(trip, expected);
        emit(EventTypes.TRIP_ARRIVED, trip,
                new TripDriverPayload(tripId.toString(), driverId), now);
    }

    @Transactional
    public void start(UUID tripId, String driverId) {
        Trip trip = load(tripId);
        requireDriver(trip, driverId);
        if (trip.state == TripState.IN_PROGRESS) {
            return;
        }
        if (trip.state != TripState.ARRIVED) {
            throw conflict(trip, TripState.IN_PROGRESS);
        }
        Instant now = clock.instant();
        TripState expected = trip.state;
        trip.state = TripState.IN_PROGRESS;
        trip.startedAt = now;
        persist(trip, expected);
        emit(EventTypes.TRIP_STARTED, trip,
                new TripDriverPayload(tripId.toString(), driverId), now);
    }

    @Transactional
    public void complete(UUID tripId, String driverId) {
        Trip trip = load(tripId);
        requireDriver(trip, driverId);
        if (trip.state == TripState.COMPLETED) {
            return;
        }
        if (trip.state != TripState.IN_PROGRESS) {
            throw conflict(trip, TripState.COMPLETED);
        }
        Instant now = clock.instant();
        TripState expected = trip.state;
        trip.state = TripState.COMPLETED;
        trip.completedAt = now;
        persist(trip, expected);
        emit(EventTypes.TRIP_COMPLETED, trip, new TripCompletedPayload(
                tripId.toString(), trip.riderId, driverId,
                trip.startedAt, now,
                trip.pickupLat, trip.pickupLng,
                trip.dropoffLat, trip.dropoffLng), now);
    }

    @Transactional
    public void markUnmatched(UUID tripId) {
        Trip trip = load(tripId);
        if (trip.state == TripState.UNMATCHED) {
            return;
        }
        if (trip.state != TripState.REQUESTED && trip.state != TripState.MATCHING) {
            return;
        }
        Instant now = clock.instant();
        TripState expected = trip.state;
        trip.state = TripState.UNMATCHED;
        trip.unmatchedAt = now;
        persist(trip, expected);
        emit(EventTypes.TRIP_UNMATCHED, trip,
                new TripLifecyclePayload(tripId.toString()), now);
    }

    private Trip load(UUID tripId) {
        return store.load(tripId).orElseThrow(TripNotFoundException::new);
    }

    private void requireDriver(Trip trip, String driverId) {
        if (trip.driverId == null || !driverId.equals(trip.driverId)) {
            throw new TripForbiddenException();
        }
    }

    private void persist(Trip trip, TripState expected) {
        if (!store.save(trip, expected.wireValue())) {
            throw new TripConflictException("trip " + trip.id + " changed concurrently");
        }
    }

    private void emit(String type, Trip trip, Object payload, Instant occurredAt) {
        sink.append(new TripEvent(type, trip.id, trip.cityId, occurredAt, payload));
    }

    private TripRequestedPayload requestedPayload(Trip trip) {
        return new TripRequestedPayload(
                trip.id.toString(), trip.riderId, trip.cityId,
                trip.pickupLat, trip.pickupLng, trip.dropoffLat, trip.dropoffLng);
    }

    private TripConflictException conflict(Trip trip, TripState target) {
        return new TripConflictException(
                "trip " + trip.id + " in state " + trip.state.wireValue()
                        + " cannot move to " + target.wireValue());
    }

    private boolean validLat(double lat) {
        return lat >= -90 && lat <= 90;
    }

    private boolean validLng(double lng) {
        return lng >= -180 && lng <= 180;
    }
}

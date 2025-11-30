package com.cdwater.ridego.trip.trip;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.TripCancelledPayload;
import com.cdwater.ridego.contracts.TripCompletedPayload;
import com.cdwater.ridego.contracts.TripDriverCancelledPayload;
import com.cdwater.ridego.contracts.TripDriverPayload;
import com.cdwater.ridego.contracts.TripRequestedPayload;
import com.cdwater.ridego.trip.domain.TripState;
import com.cdwater.ridego.trip.outbox.OutboxRepository;
import com.cdwater.ridego.trip.web.BadRequestException;
import com.cdwater.ridego.trip.web.ConflictException;
import com.cdwater.ridego.trip.web.ForbiddenException;
import com.cdwater.ridego.trip.web.TripNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class TripService {

    private static final String DEFAULT_CITY = "city-1";
    private static final Set<String> RIDER_CANCELLABLE =
            Set.of("requested", "matching", "offered", "accepted", "arrived");
    private static final Set<String> DRIVER_CANCELLABLE = Set.of("accepted", "arrived");

    private final TripRepository trips;
    private final OutboxRepository outbox;
    private final TripEvents events;
    private final long matchingWindowSeconds;

    public TripService(TripRepository trips,
                       OutboxRepository outbox,
                       TripEvents events,
                       @Value("${ridego.matching.window-seconds:90}") long matchingWindowSeconds) {
        this.trips = trips;
        this.outbox = outbox;
        this.events = events;
        this.matchingWindowSeconds = matchingWindowSeconds;
    }

    @Transactional
    public TripResponse create(String riderId, CreateTripRequest request) {
        validate(request);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Instant deadline = now.plusSeconds(matchingWindowSeconds);
        String cityId = request.cityId() == null || request.cityId().isBlank()
                ? DEFAULT_CITY
                : request.cityId();

        trips.insert(id, cityId, riderId,
                request.pickupLat(), request.pickupLng(),
                request.dropoffLat(), request.dropoffLng(), now, deadline);

        TripRequestedPayload payload = new TripRequestedPayload(
                id.toString(), riderId, cityId,
                request.pickupLat(), request.pickupLng(),
                request.dropoffLat(), request.dropoffLng());
        EventEnvelope<TripRequestedPayload> event = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                EventTypes.TRIP_REQUESTED,
                1,
                now,
                id.toString(),
                cityId,
                id.toString(),
                null,
                payload);
        outbox.append("trip.events.v1", id.toString(), event);

        return new TripResponse(id.toString(), TripState.REQUESTED.wireValue());
    }

    public TripView get(String userId, String driverId, UUID id) {
        TripView view = trips.findReadModel(id).orElseThrow(TripNotFoundException::new);
        boolean allowed = view.riderId().equals(userId)
                || (driverId != null && driverId.equals(view.driverId()));
        if (!allowed) {
            throw new TripNotFoundException();
        }
        return view;
    }

    @Transactional
    public void cancel(UUID id, String userId, String driverId, String reason) {
        TripRecord trip = trips.find(id).orElseThrow(TripNotFoundException::new);
        String cancelReason = reason == null || reason.isBlank() ? "user_requested" : reason;

        boolean driverCancelling = driverId != null && driverId.equals(trip.driverId());
        boolean riderCancelling = userId != null && userId.equals(trip.riderId());
        if (!driverCancelling && !riderCancelling) {
            throw new ForbiddenException();
        }

        if (driverCancelling) {
            if (!DRIVER_CANCELLABLE.contains(trip.state())) {
                throw new ConflictException("driver cannot cancel trip in state " + trip.state());
            }
            Instant deadline = Instant.now().plusSeconds(matchingWindowSeconds);
            if (trips.updateToMatchingForRedispatch(id, trip.state(), deadline) == 0) {
                throw new ConflictException("trip state changed, please retry");
            }
            events.append(EventTypes.TRIP_DRIVER_CANCELLED, trip,
                    new TripDriverCancelledPayload(id.toString(), driverId, cancelReason));
            events.append(EventTypes.TRIP_MATCHING_RESTARTED, trip, events.requestedPayload(trip));
            return;
        }

        if (!RIDER_CANCELLABLE.contains(trip.state())) {
            throw new ConflictException("rider cannot cancel trip in state " + trip.state());
        }
        if (trips.updateToCancelled(id, trip.state(), "rider", cancelReason) == 0) {
            throw new ConflictException("trip state changed, please retry");
        }
        events.append(EventTypes.TRIP_CANCELLED, trip,
                new TripCancelledPayload(id.toString(), "rider", cancelReason));
    }

    @Transactional
    public void arrive(UUID id, String driverId) {
        TripRecord trip = requireDriverState(id, driverId, "accepted");
        if (trips.updateToArrived(id, "accepted") == 0) {
            throw new ConflictException("trip is no longer accepted");
        }
        events.append(EventTypes.TRIP_ARRIVED, trip,
                new TripDriverPayload(id.toString(), driverId));
    }

    @Transactional
    public void start(UUID id, String driverId) {
        TripRecord trip = requireDriverState(id, driverId, "arrived");
        if (trips.updateToStarted(id, "arrived") == 0) {
            throw new ConflictException("trip is no longer arrived");
        }
        events.append(EventTypes.TRIP_STARTED, trip,
                new TripDriverPayload(id.toString(), driverId));
    }

    @Transactional
    public void complete(UUID id, String driverId) {
        TripRecord trip = requireDriverState(id, driverId, "in_progress");
        if (trips.updateToCompleted(id, "in_progress") == 0) {
            throw new ConflictException("trip is no longer in progress");
        }
        Instant completedAt = Instant.now();
        events.append(EventTypes.TRIP_COMPLETED, trip, new TripCompletedPayload(
                trip.id().toString(),
                trip.riderId(),
                driverId,
                trip.startedAt(),
                completedAt,
                trip.pickupLat(),
                trip.pickupLng(),
                trip.dropoffLat(),
                trip.dropoffLng()));
    }

    private TripRecord requireDriverState(UUID id, String driverId, String expectedState) {
        TripRecord trip = trips.find(id).orElseThrow(TripNotFoundException::new);
        if (!driverId.equals(trip.driverId())) {
            throw new ForbiddenException();
        }
        if (!expectedState.equals(trip.state())) {
            throw new ConflictException("trip state is " + trip.state() + ", expected " + expectedState);
        }
        return trip;
    }

    private void validate(CreateTripRequest request) {
        if (request == null) {
            throw new BadRequestException("request body is required");
        }
        if (!validLat(request.pickupLat()) || !validLat(request.dropoffLat())
                || !validLng(request.pickupLng()) || !validLng(request.dropoffLng())) {
            throw new BadRequestException("invalid coordinates");
        }
    }

    private boolean validLat(double lat) {
        return lat >= -90 && lat <= 90;
    }

    private boolean validLng(double lng) {
        return lng >= -180 && lng <= 180;
    }
}

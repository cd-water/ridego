package com.cdwater.ridego.trip.trip;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.TripCompletedPayload;
import com.cdwater.ridego.contracts.TripDriverPayload;
import com.cdwater.ridego.contracts.TripRequestedPayload;
import com.cdwater.ridego.trip.domain.TripState;
import com.cdwater.ridego.trip.outbox.OutboxRepository;
import com.cdwater.ridego.trip.web.BadRequestException;
import com.cdwater.ridego.trip.web.ConflictException;
import com.cdwater.ridego.trip.web.ForbiddenException;
import com.cdwater.ridego.trip.web.TripNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TripService {

    private static final String DEFAULT_CITY = "city-1";

    private final TripRepository trips;
    private final OutboxRepository outbox;
    private final TripEvents events;

    public TripService(TripRepository trips, OutboxRepository outbox, TripEvents events) {
        this.trips = trips;
        this.outbox = outbox;
        this.events = events;
    }

    @Transactional
    public TripResponse create(String riderId, CreateTripRequest request) {
        validate(request);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String cityId = request.cityId() == null || request.cityId().isBlank()
                ? DEFAULT_CITY
                : request.cityId();

        trips.insert(id, cityId, riderId,
                request.pickupLat(), request.pickupLng(),
                request.dropoffLat(), request.dropoffLng(), now);

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

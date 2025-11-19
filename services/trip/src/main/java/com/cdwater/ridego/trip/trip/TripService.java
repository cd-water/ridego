package com.cdwater.ridego.trip.trip;

import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.TripRequestedPayload;
import com.cdwater.ridego.trip.domain.TripState;
import com.cdwater.ridego.trip.outbox.OutboxRepository;
import com.cdwater.ridego.trip.web.BadRequestException;
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

    public TripService(TripRepository trips, OutboxRepository outbox) {
        this.trips = trips;
        this.outbox = outbox;
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

    public TripView get(String riderId, UUID id) {
        TripView view = trips.findReadModel(id).orElseThrow(TripNotFoundException::new);
        if (!view.riderId().equals(riderId)) {
            throw new TripNotFoundException();
        }
        return view;
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

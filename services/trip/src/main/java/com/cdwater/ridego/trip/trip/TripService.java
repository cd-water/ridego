package com.cdwater.ridego.trip.trip;

import com.cdwater.ridego.trip.lifecycle.ActorType;
import com.cdwater.ridego.trip.lifecycle.TripLifecycle;
import com.cdwater.ridego.trip.lifecycle.TripNotFoundException;
import com.cdwater.ridego.trip.lifecycle.TripState;
import com.cdwater.ridego.trip.read.TripReadRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TripService {

    private static final String DEFAULT_CITY = "city-1";

    private final TripLifecycle lifecycle;
    private final TripReadRepository readRepository;

    public TripService(TripLifecycle lifecycle, TripReadRepository readRepository) {
        this.lifecycle = lifecycle;
        this.readRepository = readRepository;
    }

    public TripResponse create(String riderId, CreateTripRequest request) {
        String cityId = request.cityId() == null || request.cityId().isBlank()
                ? DEFAULT_CITY
                : request.cityId();
        UUID id = lifecycle.request(riderId, cityId,
                request.pickupLat(), request.pickupLng(),
                request.dropoffLat(), request.dropoffLng());
        return new TripResponse(id.toString(), TripState.REQUESTED.wireValue());
    }

    public TripView get(String userId, String driverId, UUID id) {
        TripView view = readRepository.findById(id).orElseThrow(TripNotFoundException::new);
        boolean allowed = view.riderId().equals(userId)
                || (driverId != null && driverId.equals(view.driverId()));
        if (!allowed) {
            throw new TripNotFoundException();
        }
        return view;
    }

    public void cancel(UUID id, String userId, String driverId, String reason) {
        boolean driverIntent = driverId != null;
        lifecycle.cancel(id,
                driverIntent ? ActorType.DRIVER : ActorType.RIDER,
                driverIntent ? driverId : userId,
                reason);
    }

    public void arrive(UUID id, String driverId) {
        lifecycle.arrive(id, driverId);
    }

    public void start(UUID id, String driverId) {
        lifecycle.start(id, driverId);
    }

    public void complete(UUID id, String driverId) {
        lifecycle.complete(id, driverId);
    }
}

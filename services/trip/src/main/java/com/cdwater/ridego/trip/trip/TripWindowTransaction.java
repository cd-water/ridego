package com.cdwater.ridego.trip.trip;

import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.TripLifecyclePayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripWindowTransaction {

    private final TripRepository trips;
    private final TripEvents events;

    public TripWindowTransaction(TripRepository trips, TripEvents events) {
        this.trips = trips;
        this.events = events;
    }

    @Transactional
    public void markUnmatched(TripRecord trip) {
        if (trips.updateToUnmatched(trip.id(), trip.state()) == 0) {
            return;
        }
        events.append(EventTypes.TRIP_UNMATCHED, trip,
                new TripLifecyclePayload(trip.id().toString()));
    }
}

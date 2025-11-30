package com.cdwater.ridego.trip.trip;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TripMatchingWindowScheduler {

    private final TripRepository trips;
    private final TripWindowTransaction transaction;

    public TripMatchingWindowScheduler(TripRepository trips, TripWindowTransaction transaction) {
        this.trips = trips;
        this.transaction = transaction;
    }

    @Scheduled(fixedDelayString = "${ridego.matching.scan-ms:1000}")
    public void expireMatchingWindows() {
        for (TripRecord trip : trips.findMatchingExpired(Instant.now(), 100)) {
            transaction.markUnmatched(trip);
        }
    }
}

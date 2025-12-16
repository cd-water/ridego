package com.cdwater.ridego.trip.trip;

import com.cdwater.ridego.trip.lifecycle.TripLifecycle;
import com.cdwater.ridego.trip.lifecycle.TripNotFoundException;
import com.cdwater.ridego.trip.read.TripReadRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
public class TripMatchingWindowScheduler {

    private final TripReadRepository readRepository;
    private final TripLifecycle lifecycle;
    private final Clock clock;

    public TripMatchingWindowScheduler(TripReadRepository readRepository,
                                       TripLifecycle lifecycle,
                                       Clock clock) {
        this.readRepository = readRepository;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${ridego.matching.scan-ms:1000}")
    public void expireMatchingWindows() {
        for (UUID id : readRepository.findMatchingExpired(clock.instant(), 100)) {
            try {
                lifecycle.markUnmatched(id);
            } catch (TripNotFoundException e) {
                // trip disappeared between query and intent; nothing to do
            }
        }
    }
}

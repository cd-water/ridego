package com.cdwater.ridego.trip.lifecycle;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum TripState {

    REQUESTED,
    MATCHING,
    OFFERED,
    ACCEPTED,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    UNMATCHED;

    private static final Map<TripState, Set<TripState>> ALLOWED = Map.of(
            REQUESTED, Set.of(MATCHING, CANCELLED),
            MATCHING, Set.of(OFFERED, UNMATCHED, CANCELLED),
            OFFERED, Set.of(ACCEPTED, MATCHING, CANCELLED),
            ACCEPTED, Set.of(ARRIVED, MATCHING, CANCELLED),
            ARRIVED, Set.of(IN_PROGRESS, MATCHING, CANCELLED),
            IN_PROGRESS, Set.of(COMPLETED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of(),
            UNMATCHED, Set.of()
    );

    public boolean canTransitionTo(TripState next) {
        return ALLOWED.get(this).contains(next);
    }

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TripState from(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}

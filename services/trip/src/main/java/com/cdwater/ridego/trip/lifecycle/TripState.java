package com.cdwater.ridego.trip.lifecycle;

import com.cdwater.ridego.contracts.EventTypes;

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

    /**
     * The state a Trip enters after each write-side event.
     * {@code TripAcceptRejected} does not change state and returns {@code null}.
     */
    public static TripState stateOfEvent(String eventType) {
        return switch (eventType) {
            case EventTypes.TRIP_REQUESTED -> REQUESTED;
            case EventTypes.TRIP_MATCHING_STARTED -> MATCHING;
            case EventTypes.TRIP_OFFERED -> OFFERED;
            case EventTypes.TRIP_MATCHING_RESTARTED -> MATCHING;
            case EventTypes.TRIP_DRIVER_CANCELLED -> MATCHING;
            case EventTypes.TRIP_ACCEPTED -> ACCEPTED;
            case EventTypes.TRIP_ARRIVED -> ARRIVED;
            case EventTypes.TRIP_STARTED -> IN_PROGRESS;
            case EventTypes.TRIP_COMPLETED -> COMPLETED;
            case EventTypes.TRIP_CANCELLED -> CANCELLED;
            case EventTypes.TRIP_UNMATCHED -> UNMATCHED;
            default -> null;
        };
    }
}

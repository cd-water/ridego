package com.cdwater.ridego.trip.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripStateTest {

    @Test
    void allowsHappyPath() {
        assertTrue(TripState.REQUESTED.canTransitionTo(TripState.MATCHING));
        assertTrue(TripState.MATCHING.canTransitionTo(TripState.OFFERED));
        assertTrue(TripState.OFFERED.canTransitionTo(TripState.ACCEPTED));
        assertTrue(TripState.ACCEPTED.canTransitionTo(TripState.ARRIVED));
        assertTrue(TripState.ARRIVED.canTransitionTo(TripState.IN_PROGRESS));
        assertTrue(TripState.IN_PROGRESS.canTransitionTo(TripState.COMPLETED));
    }

    @Test
    void allowsRedispatchAndTerminalTransitions() {
        assertTrue(TripState.OFFERED.canTransitionTo(TripState.MATCHING));
        assertTrue(TripState.ACCEPTED.canTransitionTo(TripState.MATCHING));
        assertTrue(TripState.ARRIVED.canTransitionTo(TripState.MATCHING));
        assertTrue(TripState.MATCHING.canTransitionTo(TripState.UNMATCHED));
        assertTrue(TripState.ARRIVED.canTransitionTo(TripState.CANCELLED));
    }

    @Test
    void rejectsIllegalTransitions() {
        assertFalse(TripState.REQUESTED.canTransitionTo(TripState.ACCEPTED));
        assertFalse(TripState.MATCHING.canTransitionTo(TripState.COMPLETED));
        assertFalse(TripState.IN_PROGRESS.canTransitionTo(TripState.CANCELLED));
    }

    @Test
    void terminalStatesAreImmutable() {
        for (TripState terminal : new TripState[]{
                TripState.COMPLETED, TripState.CANCELLED, TripState.UNMATCHED}) {
            for (TripState next : TripState.values()) {
                assertFalse(terminal.canTransitionTo(next), terminal + " -> " + next);
            }
        }
    }
}

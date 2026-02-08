package com.cdwater.ridego.trip.lifecycle;

import com.cdwater.ridego.contracts.EventTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertTrue(TripState.REQUESTED.canTransitionTo(TripState.UNMATCHED));
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
    void stateOfEventMapsWriteSideEvents() {
        assertEquals(TripState.REQUESTED, TripState.stateOfEvent(EventTypes.TRIP_REQUESTED));
        assertEquals(TripState.MATCHING, TripState.stateOfEvent(EventTypes.TRIP_MATCHING_STARTED));
        assertEquals(TripState.OFFERED, TripState.stateOfEvent(EventTypes.TRIP_OFFERED));
        assertEquals(TripState.MATCHING, TripState.stateOfEvent(EventTypes.TRIP_MATCHING_RESTARTED));
        assertEquals(TripState.MATCHING, TripState.stateOfEvent(EventTypes.TRIP_DRIVER_CANCELLED));
        assertEquals(TripState.ACCEPTED, TripState.stateOfEvent(EventTypes.TRIP_ACCEPTED));
        assertEquals(TripState.ARRIVED, TripState.stateOfEvent(EventTypes.TRIP_ARRIVED));
        assertEquals(TripState.IN_PROGRESS, TripState.stateOfEvent(EventTypes.TRIP_STARTED));
        assertEquals(TripState.COMPLETED, TripState.stateOfEvent(EventTypes.TRIP_COMPLETED));
        assertEquals(TripState.CANCELLED, TripState.stateOfEvent(EventTypes.TRIP_CANCELLED));
        assertEquals(TripState.UNMATCHED, TripState.stateOfEvent(EventTypes.TRIP_UNMATCHED));
        assertNull(TripState.stateOfEvent(EventTypes.TRIP_ACCEPT_REJECTED));
        assertNull(TripState.stateOfEvent("Unknown"));
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

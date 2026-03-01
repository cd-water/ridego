package com.cdwater.ridego.simulator.fleet;

import com.cdwater.ridego.contracts.DriverPositionPayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverFleetTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    @Test
    void sameSeedAndTickReproducePositions() {
        DriverPositionPayload a = fleet(42).positionAt(7, 3, NOW);
        DriverPositionPayload b = fleet(42).positionAt(7, 3, NOW);

        assertEquals(a, b);
    }

    @Test
    void differentSeedChangesJitter() {
        assertNotEquals(fleet(42).positionAt(7, 3, NOW), fleet(43).positionAt(7, 3, NOW));
    }

    @Test
    void tickChangesPosition() {
        DriverFleet fleet = fleet(42);

        assertNotEquals(fleet.positionAt(7, 3, NOW), fleet.positionAt(7, 4, NOW));
    }

    @Test
    void driverIdsAndGridMatchSize() {
        DriverFleet fleet = fleet(42);

        assertEquals(10, fleet.size());
        assertEquals(10, fleet.driverIds().size());
        assertEquals("sim-driver-0", fleet.driverIds().getFirst());
        assertEquals(4, fleet.gridSide());
    }

    @Test
    void chanceIsSeededAndBounded() {
        DriverFleet fleet = fleet(42);

        assertEquals(fleet.chance("trip-1:offer-1", 1, 0.5),
                fleet.chance("trip-1:offer-1", 1, 0.5));
        assertFalse(fleet.chance("trip-1", 1, 0.0));
        assertTrue(fleet.chance("trip-1", 1, 1.0));
    }

    private DriverFleet fleet(long seed) {
        return new DriverFleet(10, "city-1", 31.2304, 121.4737, seed, 0.0005);
    }
}

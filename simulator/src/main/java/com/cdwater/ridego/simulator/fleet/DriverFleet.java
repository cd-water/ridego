package com.cdwater.ridego.simulator.fleet;

import com.cdwater.ridego.contracts.DriverPositionPayload;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * A synthetic fleet laid out on a grid around the pickup point. Positions and
 * accept/reject decisions are derived from the seed, driver index and tick, so
 * the same seed reproduces the same run regardless of thread scheduling.
 */
public class DriverFleet {

    private static final double GRID_SPACING_DEGREES = 0.002;

    private final int size;
    private final String cityId;
    private final double centerLat;
    private final double centerLng;
    private final double jitterDegrees;
    private final long seed;
    private final List<String> driverIds;

    public DriverFleet(int size,
                       String cityId,
                       double centerLat,
                       double centerLng,
                       long seed,
                       double jitterDegrees) {
        if (size < 1) {
            throw new IllegalArgumentException("fleet size must be positive");
        }
        this.size = size;
        this.cityId = cityId;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.seed = seed;
        this.jitterDegrees = jitterDegrees;
        this.driverIds = IntStream.range(0, size)
                .mapToObj(index -> "sim-driver-" + index)
                .toList();
    }

    public List<String> driverIds() {
        return driverIds;
    }

    public int size() {
        return size;
    }

    public long seed() {
        return seed;
    }

    public int gridSide() {
        return Math.max(1, (int) Math.ceil(Math.sqrt(size)));
    }

    public DriverPositionPayload positionAt(int index, long tick, Instant now) {
        int side = gridSide();
        int x = index % side;
        int y = index / side;
        Random random = new Random(mix(index, tick));
        double jitterLat = (random.nextDouble() * 2 - 1) * jitterDegrees;
        double jitterLng = (random.nextDouble() * 2 - 1) * jitterDegrees;
        return new DriverPositionPayload(
                driverIds.get(index),
                cityId,
                centerLat + (x - side / 2.0) * GRID_SPACING_DEGREES + jitterLat,
                centerLng + (y - side / 2.0) * GRID_SPACING_DEGREES + jitterLng,
                now);
    }

    /**
     * Deterministic decision for one logical actor (trip, offer, ...).
     */
    public boolean chance(String key, int salt, double rate) {
        return new Random(mix(key.hashCode(), salt)).nextDouble() < rate;
    }

    private long mix(long a, long b) {
        return seed * 1_000_003L + a * 31L + b;
    }
}

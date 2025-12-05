package com.cdwater.ridego.location.geo;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ActiveTripRegistry {

    private static final String ACTIVE_TRIP_PREFIX = "active:trip:";
    private static final String TRIP_DRIVER_PREFIX = "trip:driver:";
    private static final Duration TTL = Duration.ofHours(6);

    private final StringRedisTemplate redis;

    public ActiveTripRegistry(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void start(String tripId, String driverId) {
        redis.opsForValue().set(ACTIVE_TRIP_PREFIX + driverId, tripId, TTL);
        redis.opsForValue().set(TRIP_DRIVER_PREFIX + tripId, driverId, TTL);
    }

    public String tripForDriver(String driverId) {
        return redis.opsForValue().get(ACTIVE_TRIP_PREFIX + driverId);
    }

    public void endByDriver(String driverId) {
        String tripId = redis.opsForValue().get(ACTIVE_TRIP_PREFIX + driverId);
        redis.delete(ACTIVE_TRIP_PREFIX + driverId);
        if (tripId != null) {
            redis.delete(TRIP_DRIVER_PREFIX + tripId);
        }
    }

    public void endByTrip(String tripId) {
        String driverId = redis.opsForValue().get(TRIP_DRIVER_PREFIX + tripId);
        redis.delete(TRIP_DRIVER_PREFIX + tripId);
        if (driverId != null) {
            redis.delete(ACTIVE_TRIP_PREFIX + driverId);
        }
    }
}

package com.cdwater.ridego.location.geo;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DriverLocationService {

    private static final String GEO_KEY_PREFIX = "geo:drivers:";
    private static final String PRESENCE_KEY_PREFIX = "driver:presence:";
    private static final String LAST_SEEN_KEY_PREFIX = "driver:last_seen:";
    private static final String CITY_KEY_PREFIX = "driver:city:";
    private static final String ONLINE_SET = "drivers:online";

    private final StringRedisTemplate redis;

    public DriverLocationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void ingestPosition(String driverId, String cityId, double lat, double lng) {
        geo().add(GEO_KEY_PREFIX + cityId, new Point(lng, lat), driverId);
        redis.opsForValue().set(LAST_SEEN_KEY_PREFIX + driverId, Instant.now().toString());
    }

    public void setPresence(String driverId, String cityId, boolean online) {
        redis.opsForValue().set(PRESENCE_KEY_PREFIX + driverId, online ? "online" : "offline");
        redis.opsForValue().set(CITY_KEY_PREFIX + driverId, cityId);
        if (online) {
            redis.opsForValue().set(LAST_SEEN_KEY_PREFIX + driverId, Instant.now().toString());
            redis.opsForSet().add(ONLINE_SET, driverId);
        } else {
            redis.opsForSet().remove(ONLINE_SET, driverId);
            geo().remove(GEO_KEY_PREFIX + cityId, driverId);
        }
    }

    public java.util.Set<String> onlineDrivers() {
        java.util.Set<String> members = redis.opsForSet().members(ONLINE_SET);
        return members == null ? java.util.Set.of() : members;
    }

    public Instant lastSeen(String driverId) {
        String value = redis.opsForValue().get(LAST_SEEN_KEY_PREFIX + driverId);
        return value == null ? null : Instant.parse(value);
    }

    public String cityOf(String driverId) {
        String cityId = redis.opsForValue().get(CITY_KEY_PREFIX + driverId);
        return cityId == null ? "city-1" : cityId;
    }

    public List<NearbyDriver> nearby(String cityId, double lat, double lng, double radiusKm, int limit) {
        GeoSearchCommandArgs args = GeoSearchCommandArgs.newGeoSearchArgs()
                .includeDistance()
                .sortAscending()
                .limit(limit);

        GeoResults<GeoLocation<String>> results = geo().search(
                GEO_KEY_PREFIX + cityId,
                GeoReference.fromCoordinate(lng, lat),
                new Distance(radiusKm, Metrics.KILOMETERS),
                args);

        List<String> driverIds = results.getContent().stream()
                .map(result -> result.getContent().getName())
                .toList();
        if (driverIds.isEmpty()) {
            return List.of();
        }

        List<String> presenceKeys = driverIds.stream()
                .map(id -> PRESENCE_KEY_PREFIX + id)
                .toList();
        List<String> presences = redis.opsForValue().multiGet(presenceKeys);

        return java.util.stream.IntStream.range(0, driverIds.size())
                .filter(i -> presences != null && "online".equals(presences.get(i)))
                .mapToObj(i -> new NearbyDriver(
                        driverIds.get(i),
                        results.getContent().get(i).getDistance().getValue() * 1000))
                .toList();
    }

    private GeoOperations<String, String> geo() {
        return redis.opsForGeo();
    }

    public record NearbyDriver(String driverId, double distanceMeters) {
    }
}

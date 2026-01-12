package com.cdwater.ridego.location.presence;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisDriverPresenceStore implements DriverPresenceStore {

    private static final String GEO_KEY_PREFIX = "geo:drivers:";
    private static final String PRESENCE_KEY_PREFIX = "driver:presence:";
    private static final String LAST_SEEN_KEY_PREFIX = "driver:last_seen:";
    private static final String CITY_KEY_PREFIX = "driver:city:";
    private static final String ONLINE_SET = "drivers:online";

    private final StringRedisTemplate redis;

    public RedisDriverPresenceStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void recordOnline(String driverId, String cityId, Instant now) {
        redis.opsForValue().set(PRESENCE_KEY_PREFIX + driverId, "online");
        redis.opsForValue().set(CITY_KEY_PREFIX + driverId, cityId);
        redis.opsForValue().set(LAST_SEEN_KEY_PREFIX + driverId, now.toString());
        redis.opsForSet().add(ONLINE_SET, driverId);
    }

    @Override
    public void recordOffline(String driverId, String cityId) {
        redis.opsForValue().set(PRESENCE_KEY_PREFIX + driverId, "offline");
        redis.opsForSet().remove(ONLINE_SET, driverId);
        geo().remove(GEO_KEY_PREFIX + cityId, driverId);
    }

    @Override
    public void recordPosition(String driverId, String cityId, double lat, double lng, Instant now) {
        geo().add(GEO_KEY_PREFIX + cityId, new Point(lng, lat), driverId);
        redis.opsForValue().set(LAST_SEEN_KEY_PREFIX + driverId, now.toString());
    }

    @Override
    public Set<String> onlineDrivers() {
        Set<String> members = redis.opsForSet().members(ONLINE_SET);
        return members == null ? Set.of() : members;
    }

    @Override
    public Optional<Instant> lastSeen(String driverId) {
        String value = redis.opsForValue().get(LAST_SEEN_KEY_PREFIX + driverId);
        return value == null ? Optional.empty() : Optional.of(Instant.parse(value));
    }

    @Override
    public String cityOf(String driverId) {
        String cityId = redis.opsForValue().get(CITY_KEY_PREFIX + driverId);
        return cityId == null ? "city-1" : cityId;
    }

    @Override
    public List<NearbyDriver> nearby(String cityId, double lat, double lng,
                                     double radiusKm, int limit) {
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
}

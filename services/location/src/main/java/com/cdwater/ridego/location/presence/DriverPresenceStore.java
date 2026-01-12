package com.cdwater.ridego.location.presence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DriverPresenceStore {

    void recordOnline(String driverId, String cityId, Instant now);

    void recordOffline(String driverId, String cityId);

    void recordPosition(String driverId, String cityId, double lat, double lng, Instant now);

    Set<String> onlineDrivers();

    Optional<Instant> lastSeen(String driverId);

    String cityOf(String driverId);

    List<NearbyDriver> nearby(String cityId, double lat, double lng, double radiusKm, int limit);
}

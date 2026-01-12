package com.cdwater.ridego.location.presence;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

public class DriverPresence {

    private final DriverPresenceStore store;
    private final PresenceEventSink events;
    private final Clock clock;
    private final long timeoutSeconds;

    public DriverPresence(DriverPresenceStore store,
                          PresenceEventSink events,
                          Clock clock,
                          long timeoutSeconds) {
        this.store = store;
        this.events = events;
        this.clock = clock;
        this.timeoutSeconds = timeoutSeconds;
    }

    public void markOnline(String driverId, String cityId) {
        store.recordOnline(driverId, cityId, clock.instant());
        events.publish(driverId, cityId, true);
    }

    public void markOffline(String driverId, String cityId) {
        store.recordOffline(driverId, cityId);
        events.publish(driverId, cityId, false);
    }

    public void recordPosition(String driverId, String cityId, double lat, double lng) {
        store.recordPosition(driverId, cityId, lat, lng, clock.instant());
    }

    public void sweepStale() {
        Instant cutoff = clock.instant().minusSeconds(timeoutSeconds);
        for (String driverId : store.onlineDrivers()) {
            Instant lastSeen = store.lastSeen(driverId).orElse(null);
            if (lastSeen != null && !lastSeen.isBefore(cutoff)) {
                continue;
            }
            String cityId = store.cityOf(driverId);
            store.recordOffline(driverId, cityId);
            events.publish(driverId, cityId, false);
        }
    }

    public List<NearbyDriver> nearby(String cityId, double lat, double lng,
                                     double radiusKm, int limit) {
        return store.nearby(cityId, lat, lng, radiusKm, limit);
    }
}

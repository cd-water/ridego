package com.cdwater.ridego.location.presence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverPresenceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private InMemoryStore store;
    private RecordingSink sink;
    private DriverPresence presence;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        sink = new RecordingSink();
        presence = new DriverPresence(store, sink, Clock.fixed(NOW, ZoneOffset.UTC), 30);
    }

    @Test
    void markOnlineRecordsAndPublishes() {
        presence.markOnline("driver-1", "city-1");

        assertTrue(store.online.contains("driver-1"));
        assertEquals(NOW, store.lastSeen.get("driver-1"));
        assertEquals(new Change("driver-1", "city-1", true), sink.changes.get(0));
    }

    @Test
    void markOfflineRemovesAndPublishes() {
        presence.markOnline("driver-1", "city-1");
        presence.markOffline("driver-1", "city-1");

        assertFalse(store.online.contains("driver-1"));
        assertEquals(new Change("driver-1", "city-1", false), sink.changes.get(1));
    }

    @Test
    void recordPositionUpdatesHeartbeatWithoutEvent() {
        presence.recordPosition("driver-1", "city-1", 31.23, 121.47);

        assertEquals(NOW, store.lastSeen.get("driver-1"));
        assertEquals(1, store.positions.size());
        assertTrue(sink.changes.isEmpty());
    }

    @Test
    void sweepStaleOfflinesOnlyExpiredDrivers() {
        store.online.add("driver-1");
        store.online.add("driver-2");
        store.lastSeen.put("driver-1", NOW.minusSeconds(60));
        store.lastSeen.put("driver-2", NOW.minusSeconds(5));
        store.cities.put("driver-1", "city-1");
        store.cities.put("driver-2", "city-1");

        presence.sweepStale();

        assertFalse(store.online.contains("driver-1"));
        assertTrue(store.online.contains("driver-2"));
        assertEquals(1, sink.changes.size());
        assertEquals(new Change("driver-1", "city-1", false), sink.changes.get(0));
    }

    @Test
    void nearbyDelegatesToStore() {
        List<NearbyDriver> expected = List.of(new NearbyDriver("driver-1", 120.0));
        store.nearbyResult = expected;

        assertEquals(expected, presence.nearby("city-1", 31.23, 121.47, 3, 50));
    }

    record Change(String driverId, String cityId, boolean online) {
    }

    static final class RecordingSink implements PresenceEventSink {
        final List<Change> changes = new ArrayList<>();

        @Override
        public void publish(String driverId, String cityId, boolean online) {
            changes.add(new Change(driverId, cityId, online));
        }
    }

    static final class InMemoryStore implements DriverPresenceStore {
        final Set<String> online = new HashSet<>();
        final Map<String, Instant> lastSeen = new HashMap<>();
        final Map<String, String> cities = new HashMap<>();
        final List<String> positions = new ArrayList<>();
        List<NearbyDriver> nearbyResult = List.of();

        @Override
        public void recordOnline(String driverId, String cityId, Instant now) {
            online.add(driverId);
            lastSeen.put(driverId, now);
            cities.put(driverId, cityId);
        }

        @Override
        public void recordOffline(String driverId, String cityId) {
            online.remove(driverId);
            cities.put(driverId, cityId);
        }

        @Override
        public void recordPosition(String driverId, String cityId, double lat, double lng, Instant now) {
            lastSeen.put(driverId, now);
            positions.add(driverId);
        }

        @Override
        public Set<String> onlineDrivers() {
            return Set.copyOf(online);
        }

        @Override
        public Optional<Instant> lastSeen(String driverId) {
            return Optional.ofNullable(lastSeen.get(driverId));
        }

        @Override
        public String cityOf(String driverId) {
            return cities.getOrDefault(driverId, "city-1");
        }

        @Override
        public List<NearbyDriver> nearby(String cityId, double lat, double lng,
                                         double radiusKm, int limit) {
            return nearbyResult;
        }
    }
}

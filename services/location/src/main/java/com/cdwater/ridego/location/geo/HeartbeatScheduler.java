package com.cdwater.ridego.location.geo;

import com.cdwater.ridego.location.web.PresenceEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class HeartbeatScheduler {

    private final DriverLocationService locationService;
    private final PresenceEventPublisher publisher;
    private final long timeoutSeconds;

    public HeartbeatScheduler(DriverLocationService locationService,
                              PresenceEventPublisher publisher,
                              @Value("${ridego.location.heartbeat-timeout-seconds:30}") long timeoutSeconds) {
        this.locationService = locationService;
        this.publisher = publisher;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${ridego.location.heartbeat-scan-ms:10000}")
    public void expireStaleDrivers() {
        Instant cutoff = Instant.now().minusSeconds(timeoutSeconds);
        for (String driverId : locationService.onlineDrivers()) {
            Instant lastSeen = locationService.lastSeen(driverId);
            if (lastSeen != null && !lastSeen.isBefore(cutoff)) {
                continue;
            }
            String cityId = locationService.cityOf(driverId);
            locationService.setPresence(driverId, cityId, false);
            publisher.publish(driverId, cityId, false);
        }
    }
}

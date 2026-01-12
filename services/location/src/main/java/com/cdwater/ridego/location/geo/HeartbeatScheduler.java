package com.cdwater.ridego.location.geo;

import com.cdwater.ridego.location.presence.DriverPresence;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HeartbeatScheduler {

    private final DriverPresence presence;

    public HeartbeatScheduler(DriverPresence presence) {
        this.presence = presence;
    }

    @Scheduled(fixedDelayString = "${ridego.location.heartbeat-scan-ms:10000}")
    public void expireStaleDrivers() {
        presence.sweepStale();
    }
}

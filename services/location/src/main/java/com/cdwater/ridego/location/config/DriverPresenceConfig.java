package com.cdwater.ridego.location.config;

import com.cdwater.ridego.location.presence.DriverPresence;
import com.cdwater.ridego.location.presence.DriverPresenceStore;
import com.cdwater.ridego.location.presence.PresenceEventSink;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DriverPresenceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public DriverPresence driverPresence(DriverPresenceStore store,
                                         PresenceEventSink events,
                                         Clock clock,
                                         @Value("${ridego.location.heartbeat-timeout-seconds:30}") long timeoutSeconds) {
        return new DriverPresence(store, events, clock, timeoutSeconds);
    }
}

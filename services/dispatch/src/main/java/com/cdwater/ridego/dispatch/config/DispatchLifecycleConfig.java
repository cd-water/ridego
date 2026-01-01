package com.cdwater.ridego.dispatch.config;

import com.cdwater.ridego.dispatch.availability.AvailabilityStore;
import com.cdwater.ridego.dispatch.availability.DriverAvailabilityModule;
import com.cdwater.ridego.dispatch.lifecycle.DedupStore;
import com.cdwater.ridego.dispatch.lifecycle.DispatchEventSink;
import com.cdwater.ridego.dispatch.lifecycle.DriverAvailability;
import com.cdwater.ridego.dispatch.lifecycle.OfferClaim;
import com.cdwater.ridego.dispatch.lifecycle.OfferLifecycle;
import com.cdwater.ridego.dispatch.lifecycle.OfferStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class DispatchLifecycleConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public DriverAvailability driverAvailability(AvailabilityStore store) {
        return new DriverAvailabilityModule(store);
    }

    @Bean
    public OfferLifecycle offerLifecycle(OfferStore store,
                                         DriverAvailability availability,
                                         OfferClaim claim,
                                         DispatchEventSink events,
                                         DedupStore dedup,
                                         Clock clock,
                                         @Value("${ridego.matching.offer-ttl-seconds:10}") long offerTtlSeconds) {
        return new OfferLifecycle(store, availability, claim, events, dedup,
                clock, Duration.ofSeconds(offerTtlSeconds));
    }
}

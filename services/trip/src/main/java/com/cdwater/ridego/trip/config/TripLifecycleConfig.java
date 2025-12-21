package com.cdwater.ridego.trip.config;

import com.cdwater.ridego.messaging.MessagingConfiguration;
import com.cdwater.ridego.messaging.OutboxRepository;
import com.cdwater.ridego.trip.lifecycle.TripEventSink;
import com.cdwater.ridego.trip.lifecycle.TripLifecycle;
import com.cdwater.ridego.trip.lifecycle.TripStore;
import com.cdwater.ridego.trip.outbox.MetricsTripEventSink;
import com.cdwater.ridego.trip.outbox.OutboxTripEventSink;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Clock;

@Configuration
@Import(MessagingConfiguration.class)
public class TripLifecycleConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TripEventSink tripEventSink(OutboxRepository outbox,
                                       MeterRegistry meterRegistry) {
        return new MetricsTripEventSink(
                new OutboxTripEventSink(outbox), meterRegistry);
    }

    @Bean
    public TripLifecycle tripLifecycle(TripStore tripStore,
                                       TripEventSink tripEventSink,
                                       Clock clock,
                                       @Value("${ridego.matching.window-seconds:90}") long matchingWindowSeconds) {
        return new TripLifecycle(tripStore, tripEventSink, clock, matchingWindowSeconds);
    }
}

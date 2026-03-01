package com.cdwater.ridego.simulator.config;

import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import com.cdwater.ridego.simulator.client.RideGoClient;
import com.cdwater.ridego.simulator.fleet.DriverFleet;
import com.cdwater.ridego.simulator.metrics.RunMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration
public class SimulatorConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public EventEnvelopeCodec eventEnvelopeCodec(ObjectMapper objectMapper, Clock clock) {
        return new EventEnvelopeCodec(objectMapper, clock);
    }

    @Bean
    public RideGoClient rideGoClient(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${ridego.gateway-url:http://localhost:8080/api/v1}") String gatewayUrl,
            @Value("${ridego.simulator.location-url:http://localhost:8084}") String locationUrl,
            @Value("${ridego.simulator.dispatch-url:http://localhost:8083}") String dispatchUrl,
            @Value("${ridego.simulator.trip-url:http://localhost:8082}") String tripUrl) {
        return new RideGoClient(gatewayUrl, locationUrl, dispatchUrl, tripUrl,
                kafkaTemplate, objectMapper);
    }

    @Bean
    public RunMetrics runMetrics() {
        return new RunMetrics();
    }

    @Bean
    public DriverFleet driverFleet(
            @Value("${ridego.simulator.load.drivers:10000}") int drivers,
            @Value("${ridego.simulator.city-id:city-1}") String cityId,
            @Value("${ridego.simulator.load.pickup-lat:31.2304}") double pickupLat,
            @Value("${ridego.simulator.load.pickup-lng:121.4737}") double pickupLng,
            @Value("${ridego.simulator.load.seed:42}") long seed,
            @Value("${ridego.simulator.load.jitter-degrees:0.0005}") double jitterDegrees) {
        return new DriverFleet(drivers, cityId, pickupLat, pickupLng, seed, jitterDegrees);
    }
}

package com.cdwater.ridego.location.config;

import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration
public class LocationMessagingConfig {

    @Bean
    public EventEnvelopeCodec eventEnvelopeCodec(ObjectMapper objectMapper) {
        return new EventEnvelopeCodec(objectMapper, Clock.systemUTC());
    }
}

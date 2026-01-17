package com.cdwater.ridego.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class JwtConfiguration {

    @Bean
    public JwtCodec jwtCodec(@Value("${ridego.jwt.secret}") String secret,
                             @Value("${ridego.jwt.ttl-seconds:3600}") long ttlSeconds) {
        return new JwtCodec(secret, Duration.ofSeconds(ttlSeconds));
    }
}

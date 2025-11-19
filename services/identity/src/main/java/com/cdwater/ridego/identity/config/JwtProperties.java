package com.cdwater.ridego.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ridego.jwt")
public record JwtProperties(String secret, long ttlSeconds) {
}

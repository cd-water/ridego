package com.cdwater.ridego.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ridego.jwt")
public record JwtProperties(String secret) {
}

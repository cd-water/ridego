package com.cdwater.ridego.dispatch.persistence;

import com.cdwater.ridego.dispatch.lifecycle.OfferClaim;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisOfferClaim implements OfferClaim {

    private static final String KEY_PREFIX = "dispatch:claim:";

    private final StringRedisTemplate redis;

    public RedisOfferClaim(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryClaim(String driverId, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(KEY_PREFIX + driverId, "1", ttl));
    }

    @Override
    public void release(String driverId) {
        redis.delete(KEY_PREFIX + driverId);
    }
}

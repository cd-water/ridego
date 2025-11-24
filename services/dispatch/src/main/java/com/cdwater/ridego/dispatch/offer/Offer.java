package com.cdwater.ridego.dispatch.offer;

import java.time.Instant;
import java.util.UUID;

public record Offer(
        UUID id,
        UUID tripId,
        String driverId,
        String cityId,
        String state,
        Instant createdAt,
        Instant expiresAt
) {
}

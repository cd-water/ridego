package com.cdwater.ridego.identity.auth;

import java.time.Instant;
import java.util.UUID;

public record Account(
        UUID id,
        String username,
        String passwordHash,
        String role,
        String driverId,
        Instant createdAt
) {
}

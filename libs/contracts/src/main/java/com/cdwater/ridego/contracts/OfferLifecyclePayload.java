package com.cdwater.ridego.contracts;

import java.time.Instant;

public record OfferLifecyclePayload(
        String offerId,
        String tripId,
        String driverId,
        Instant expiresAt
) {
}

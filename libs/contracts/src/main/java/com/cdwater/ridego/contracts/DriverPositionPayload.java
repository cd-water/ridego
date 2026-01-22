package com.cdwater.ridego.contracts;

import java.time.Instant;

public record DriverPositionPayload(
        String driverId,
        String cityId,
        double lat,
        double lng,
        Instant ts
) {
}

package com.cdwater.ridego.dispatch.lifecycle;

import java.time.Duration;

public interface OfferClaim {

    boolean tryClaim(String driverId, Duration ttl);

    void release(String driverId);
}

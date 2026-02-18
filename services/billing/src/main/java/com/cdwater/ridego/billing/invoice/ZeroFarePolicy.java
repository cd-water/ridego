package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.contracts.TripCompletedPayload;

public class ZeroFarePolicy implements FarePolicy {

    @Override
    public int fareCents(TripCompletedPayload trip) {
        return 0;
    }
}

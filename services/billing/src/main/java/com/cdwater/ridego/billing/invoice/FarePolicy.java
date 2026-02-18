package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.contracts.TripCompletedPayload;

public interface FarePolicy {

    int fareCents(TripCompletedPayload trip);
}

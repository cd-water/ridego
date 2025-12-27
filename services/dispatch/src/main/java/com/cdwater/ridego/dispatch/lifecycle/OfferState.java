package com.cdwater.ridego.dispatch.lifecycle;

public enum OfferState {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    RELEASED;

    public String wireValue() {
        return name();
    }
}

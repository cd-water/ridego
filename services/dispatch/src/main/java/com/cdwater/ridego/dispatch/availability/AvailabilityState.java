package com.cdwater.ridego.dispatch.availability;

public enum AvailabilityState {
    IDLE,
    OFFERED,
    ON_TRIP,
    OFFLINE;

    public String wireValue() {
        return name();
    }
}

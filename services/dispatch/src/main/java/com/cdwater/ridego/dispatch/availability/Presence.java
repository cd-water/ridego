package com.cdwater.ridego.dispatch.availability;

public enum Presence {
    ONLINE,
    OFFLINE;

    public String wireValue() {
        return name();
    }
}

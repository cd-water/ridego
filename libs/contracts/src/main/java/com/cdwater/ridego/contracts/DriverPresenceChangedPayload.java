package com.cdwater.ridego.contracts;

public record DriverPresenceChangedPayload(String driverId, String cityId, boolean online) {
}

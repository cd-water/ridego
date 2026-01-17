package com.cdwater.ridego.auth;

import com.cdwater.ridego.contracts.ForbiddenException;

public record Principal(String userId, String role, String driverId) {

    public void requireRole(String expected) {
        if (!expected.equals(role)) {
            throw new ForbiddenException();
        }
    }

    public String requireDriverId() {
        if (driverId == null) {
            throw new ForbiddenException();
        }
        return driverId;
    }
}

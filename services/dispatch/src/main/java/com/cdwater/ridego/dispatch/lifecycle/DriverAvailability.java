package com.cdwater.ridego.dispatch.lifecycle;

public interface DriverAvailability {

    boolean isIdle(String driverId, String cityId);

    void markOffered(String driverId);

    void markOnTrip(String driverId);

    void release(String driverId);
}

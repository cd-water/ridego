package com.cdwater.ridego.dispatch.lifecycle;

public interface DriverAvailability {

    boolean isIdle(String driverId, String cityId);

    boolean markOffered(String driverId);

    boolean markOnTrip(String driverId);

    boolean release(String driverId);

    boolean markOnline(String driverId, String cityId);

    boolean markOffline(String driverId, String cityId);
}

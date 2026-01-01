package com.cdwater.ridego.dispatch.availability;

public class Availability {

    String driverId;
    String cityId;
    AvailabilityState state;
    Presence presence;

    public Availability(String driverId, String cityId,
                        AvailabilityState state, Presence presence) {
        this.driverId = driverId;
        this.cityId = cityId;
        this.state = state;
        this.presence = presence;
    }

    public String getDriverId() {
        return driverId;
    }

    public String getCityId() {
        return cityId;
    }

    public AvailabilityState getState() {
        return state;
    }

    public Presence getPresence() {
        return presence;
    }
}

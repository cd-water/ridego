package com.cdwater.ridego.location.presence;

public interface PresenceEventSink {

    void publish(String driverId, String cityId, boolean online);
}

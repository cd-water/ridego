package com.cdwater.ridego.trip.lifecycle;

public interface TripEventSink {

    void append(TripEvent event);
}

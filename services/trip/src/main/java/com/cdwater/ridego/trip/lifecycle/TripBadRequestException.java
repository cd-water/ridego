package com.cdwater.ridego.trip.lifecycle;

public class TripBadRequestException extends RuntimeException {

    public TripBadRequestException(String message) {
        super(message);
    }
}

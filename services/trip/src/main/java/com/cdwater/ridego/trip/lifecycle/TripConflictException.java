package com.cdwater.ridego.trip.lifecycle;

import com.cdwater.ridego.contracts.ConflictException;

public class TripConflictException extends ConflictException {

    public TripConflictException(String message) {
        super(message);
    }
}

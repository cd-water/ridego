package com.cdwater.ridego.trip.lifecycle;

import com.cdwater.ridego.contracts.BadRequestException;

public class TripBadRequestException extends BadRequestException {

    public TripBadRequestException(String message) {
        super(message);
    }
}

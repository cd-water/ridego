package com.cdwater.ridego.trip.lifecycle;

import com.cdwater.ridego.contracts.NotFoundException;

public class TripNotFoundException extends NotFoundException {

    public TripNotFoundException() {
        super("trip not found");
    }
}

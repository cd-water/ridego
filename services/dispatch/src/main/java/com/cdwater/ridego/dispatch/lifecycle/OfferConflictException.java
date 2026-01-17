package com.cdwater.ridego.dispatch.lifecycle;

import com.cdwater.ridego.contracts.ConflictException;

public class OfferConflictException extends ConflictException {

    public OfferConflictException(String message) {
        super(message);
    }
}

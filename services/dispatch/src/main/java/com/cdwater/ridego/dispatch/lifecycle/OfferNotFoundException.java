package com.cdwater.ridego.dispatch.lifecycle;

import com.cdwater.ridego.contracts.NotFoundException;

public class OfferNotFoundException extends NotFoundException {

    public OfferNotFoundException() {
        super("offer not found");
    }
}

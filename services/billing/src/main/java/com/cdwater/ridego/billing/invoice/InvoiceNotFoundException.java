package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.contracts.NotFoundException;

public class InvoiceNotFoundException extends NotFoundException {

    public InvoiceNotFoundException() {
        super("invoice not found");
    }
}

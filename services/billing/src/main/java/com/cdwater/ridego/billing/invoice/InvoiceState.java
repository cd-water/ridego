package com.cdwater.ridego.billing.invoice;

public enum InvoiceState {
    ISSUED,
    PAID;

    public String wireValue() {
        return name();
    }
}

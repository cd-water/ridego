package com.cdwater.ridego.contracts;

public record InvoicePayload(
        String invoiceId,
        String tripId,
        String riderId,
        int amountCents,
        String state
) {
}

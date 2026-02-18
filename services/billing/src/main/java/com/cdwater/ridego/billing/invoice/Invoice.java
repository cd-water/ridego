package com.cdwater.ridego.billing.invoice;

import java.time.Instant;
import java.util.UUID;

public class Invoice {

    UUID id;
    UUID tripId;
    String riderId;
    String driverId;
    int amountCents;
    InvoiceState state;
    Instant issuedAt;
    Instant paidAt;

    public Invoice(UUID id, UUID tripId, String riderId, String driverId,
                   int amountCents, InvoiceState state, Instant issuedAt, Instant paidAt) {
        this.id = id;
        this.tripId = tripId;
        this.riderId = riderId;
        this.driverId = driverId;
        this.amountCents = amountCents;
        this.state = state;
        this.issuedAt = issuedAt;
        this.paidAt = paidAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public String getRiderId() {
        return riderId;
    }

    public String getDriverId() {
        return driverId;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public InvoiceState getState() {
        return state;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}

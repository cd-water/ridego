package com.cdwater.ridego.billing.invoice;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceStore {

    Optional<Invoice> findById(UUID invoiceId);

    Optional<Invoice> findByTrip(UUID tripId);

    Optional<Invoice> findByTripAndRider(UUID tripId, String riderId);

    void insert(Invoice invoice);

    boolean save(Invoice invoice, InvoiceState expectedState);
}

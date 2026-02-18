package com.cdwater.ridego.billing.invoice;

public interface DedupStore {

    boolean markOnce(String consumer, String eventId);
}

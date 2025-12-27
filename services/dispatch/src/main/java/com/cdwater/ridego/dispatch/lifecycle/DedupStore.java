package com.cdwater.ridego.dispatch.lifecycle;

public interface DedupStore {

    boolean markOnce(String consumer, String eventId);
}

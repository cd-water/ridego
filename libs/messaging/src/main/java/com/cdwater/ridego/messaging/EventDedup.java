package com.cdwater.ridego.messaging;

@FunctionalInterface
public interface EventDedup {

    /**
     * @return true when this consumer has not processed the event before
     */
    boolean markOnce(String consumer, String eventId);
}

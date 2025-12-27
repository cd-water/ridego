package com.cdwater.ridego.dispatch.lifecycle;

import java.util.UUID;

public interface DispatchEventSink {

    void append(String eventType, UUID tripId, String cityId, Object payload);
}

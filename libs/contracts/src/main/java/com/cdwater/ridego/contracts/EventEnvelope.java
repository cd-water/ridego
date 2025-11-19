package com.cdwater.ridego.contracts;

import java.time.Instant;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String aggregateId,
        String cityId,
        String correlationId,
        String causationId,
        T payload
) {
}

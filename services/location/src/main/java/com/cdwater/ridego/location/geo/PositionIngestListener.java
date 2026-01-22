package com.cdwater.ridego.location.geo;

import com.cdwater.ridego.contracts.DriverPositionPayload;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.location.presence.DriverPresence;
import com.cdwater.ridego.location.trail.TripPosition;
import com.cdwater.ridego.location.trail.TripTrailBuffer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Component
public class PositionIngestListener {

    private final ObjectMapper objectMapper;
    private final DriverPresence presence;
    private final ActiveTripRegistry activeTrips;
    private final TripTrailBuffer trailBuffer;
    private final Counter positionsIngested;

    public PositionIngestListener(ObjectMapper objectMapper,
                                  DriverPresence presence,
                                  ActiveTripRegistry activeTrips,
                                  TripTrailBuffer trailBuffer,
                                  MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.presence = presence;
        this.activeTrips = activeTrips;
        this.trailBuffer = trailBuffer;
        this.positionsIngested = Counter.builder("ridego.positions.ingested").register(meterRegistry);
    }

    @KafkaListener(topics = Topics.DRIVER_POSITIONS, groupId = "location-ingest")
    public void onPosition(String message) {
        DriverPositionPayload payload = objectMapper.readValue(
                message, DriverPositionPayload.class);

        String driverId = payload.driverId();
        String cityId = payload.cityId() == null || payload.cityId().isBlank()
                ? "city-1"
                : payload.cityId();

        presence.recordPosition(driverId, cityId, payload.lat(), payload.lng());
        positionsIngested.increment();

        String tripId = activeTrips.tripForDriver(driverId);
        if (tripId != null) {
            Instant recordedAt = payload.ts() == null ? Instant.now() : payload.ts();
            trailBuffer.add(new TripPosition(
                    UUID.fromString(tripId), driverId, cityId,
                    payload.lat(), payload.lng(), recordedAt));
        }
    }
}

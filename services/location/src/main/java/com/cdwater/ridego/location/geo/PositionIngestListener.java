package com.cdwater.ridego.location.geo;

import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.location.trail.TripPosition;
import com.cdwater.ridego.location.trail.TripTrailBuffer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Component
public class PositionIngestListener {

    private final ObjectMapper objectMapper;
    private final DriverLocationService locationService;
    private final ActiveTripRegistry activeTrips;
    private final TripTrailBuffer trailBuffer;

    public PositionIngestListener(ObjectMapper objectMapper,
                                  DriverLocationService locationService,
                                  ActiveTripRegistry activeTrips,
                                  TripTrailBuffer trailBuffer) {
        this.objectMapper = objectMapper;
        this.locationService = locationService;
        this.activeTrips = activeTrips;
        this.trailBuffer = trailBuffer;
    }

    @KafkaListener(topics = Topics.DRIVER_POSITIONS, groupId = "location-ingest")
    public void onPosition(String message) {
        JsonNode node = objectMapper.readTree(message);
        String driverId = node.get("driverId").asText();
        String cityId = node.hasNonNull("cityId") ? node.get("cityId").asText() : "city-1";
        double lat = node.get("lat").asDouble();
        double lng = node.get("lng").asDouble();

        locationService.ingestPosition(driverId, cityId, lat, lng);

        String tripId = activeTrips.tripForDriver(driverId);
        if (tripId != null) {
            trailBuffer.add(new TripPosition(UUID.fromString(tripId), driverId, cityId, lat, lng, Instant.now()));
        }
    }
}

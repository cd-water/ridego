package com.cdwater.ridego.location.geo;

import com.cdwater.ridego.contracts.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class PositionIngestListener {

    private final ObjectMapper objectMapper;
    private final DriverLocationService locationService;

    public PositionIngestListener(ObjectMapper objectMapper, DriverLocationService locationService) {
        this.objectMapper = objectMapper;
        this.locationService = locationService;
    }

    @KafkaListener(topics = Topics.DRIVER_POSITIONS, groupId = "location-ingest")
    public void onPosition(String message) {
        JsonNode node = objectMapper.readTree(message);
        String cityId = node.hasNonNull("cityId") ? node.get("cityId").asText() : "city-1";
        locationService.ingestPosition(
                node.get("driverId").asText(),
                cityId,
                node.get("lat").asDouble(),
                node.get("lng").asDouble());
    }
}

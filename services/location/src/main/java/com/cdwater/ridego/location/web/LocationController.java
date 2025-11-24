package com.cdwater.ridego.location.web;

import com.cdwater.ridego.contracts.DriverPresenceChangedPayload;
import com.cdwater.ridego.contracts.EventEnvelope;
import com.cdwater.ridego.contracts.EventTypes;
import com.cdwater.ridego.contracts.InternalHeaders;
import com.cdwater.ridego.contracts.Topics;
import com.cdwater.ridego.location.geo.DriverLocationService;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@RestController
public class LocationController {

    private static final String DEFAULT_CITY = "city-1";

    private final DriverLocationService locationService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public LocationController(DriverLocationService locationService,
                              KafkaTemplate<String, String> kafkaTemplate,
                              ObjectMapper objectMapper) {
        this.locationService = locationService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/drivers/{driverId}/presence")
    public void presence(@PathVariable String driverId,
                         @RequestHeader(name = InternalHeaders.DRIVER_ID, required = false) String headerDriverId,
                         @RequestBody PresenceRequest request) {
        if (!driverId.equals(headerDriverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "driver mismatch");
        }
        String cityId = request.cityId() == null || request.cityId().isBlank()
                ? DEFAULT_CITY
                : request.cityId();
        locationService.setPresence(driverId, cityId, request.online());

        EventEnvelope<DriverPresenceChangedPayload> event = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                EventTypes.DRIVER_PRESENCE_CHANGED,
                1,
                Instant.now(),
                driverId,
                cityId,
                null,
                null,
                new DriverPresenceChangedPayload(driverId, cityId, request.online()));
        kafkaTemplate.send(Topics.LOCATION_EVENTS, driverId, objectMapper.writeValueAsString(event));
    }

    @GetMapping("/internal/drivers/nearby")
    public NearbyResponse nearby(@RequestParam String cityId,
                                 @RequestParam double lat,
                                 @RequestParam double lng,
                                 @RequestParam(defaultValue = "3") double radiusKm,
                                 @RequestParam(defaultValue = "50") int limit) {
        return new NearbyResponse(locationService.nearby(cityId, lat, lng, radiusKm, limit).stream()
                .map(d -> new NearbyResponse.NearbyDriver(d.driverId(), d.distanceMeters()))
                .toList());
    }
}

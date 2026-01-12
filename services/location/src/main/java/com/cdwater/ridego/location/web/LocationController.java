package com.cdwater.ridego.location.web;

import com.cdwater.ridego.contracts.InternalHeaders;
import com.cdwater.ridego.location.presence.DriverPresence;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class LocationController {

    private static final String DEFAULT_CITY = "city-1";

    private final DriverPresence presence;

    public LocationController(DriverPresence presence) {
        this.presence = presence;
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
        if (request.online()) {
            presence.markOnline(driverId, cityId);
        } else {
            presence.markOffline(driverId, cityId);
        }
    }

    @GetMapping("/internal/drivers/nearby")
    public NearbyResponse nearby(@RequestParam String cityId,
                                 @RequestParam double lat,
                                 @RequestParam double lng,
                                 @RequestParam(defaultValue = "3") double radiusKm,
                                 @RequestParam(defaultValue = "50") int limit) {
        return new NearbyResponse(presence.nearby(cityId, lat, lng, radiusKm, limit).stream()
                .map(d -> new NearbyResponse.NearbyDriver(d.driverId(), d.distanceMeters()))
                .toList());
    }
}

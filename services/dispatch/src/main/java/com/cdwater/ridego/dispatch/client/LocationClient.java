package com.cdwater.ridego.dispatch.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class LocationClient {

    private final RestClient restClient;

    public LocationClient(@Value("${ridego.location.url:http://localhost:8084}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public List<NearbyDriver> nearby(String cityId, double lat, double lng, double radiusKm, int limit) {
        NearbyResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/drivers/nearby")
                        .queryParam("cityId", cityId)
                        .queryParam("lat", lat)
                        .queryParam("lng", lng)
                        .queryParam("radiusKm", radiusKm)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(NearbyResponse.class);
        return response == null || response.drivers() == null ? List.of() : response.drivers();
    }

    public record NearbyResponse(List<NearbyDriver> drivers) {
    }

    public record NearbyDriver(String driverId, double distanceMeters) {
    }
}

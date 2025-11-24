package com.cdwater.ridego.location.web;

import java.util.List;

public record NearbyResponse(List<NearbyDriver> drivers) {

    public record NearbyDriver(String driverId, double distanceMeters) {
    }
}

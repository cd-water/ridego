package com.cdwater.ridego.simulator.scenario;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SimulatorSettings {

    public final String cityId;
    public final String riderUsername;
    public final String riderPassword;
    public final String driverUsername;
    public final String driverPassword;
    public final double pickupLat;
    public final double pickupLng;
    public final double dropoffLat;
    public final double dropoffLng;
    public final int offerTimeoutSeconds;
    public final int heartbeatWaitSeconds;

    public SimulatorSettings(
            @Value("${ridego.simulator.city-id:city-1}") String cityId,
            @Value("${ridego.simulator.rider.username:rider1}") String riderUsername,
            @Value("${ridego.simulator.rider.password:ridego}") String riderPassword,
            @Value("${ridego.simulator.driver.username:driver1}") String driverUsername,
            @Value("${ridego.simulator.driver.password:ridego}") String driverPassword,
            @Value("${ridego.simulator.pickup-lat:31.2304}") double pickupLat,
            @Value("${ridego.simulator.pickup-lng:121.4737}") double pickupLng,
            @Value("${ridego.simulator.dropoff-lat:31.2400}") double dropoffLat,
            @Value("${ridego.simulator.dropoff-lng:121.4900}") double dropoffLng,
            @Value("${ridego.simulator.offer-timeout-seconds:20}") int offerTimeoutSeconds,
            @Value("${ridego.simulator.heartbeat-wait-seconds:45}") int heartbeatWaitSeconds) {
        this.cityId = cityId;
        this.riderUsername = riderUsername;
        this.riderPassword = riderPassword;
        this.driverUsername = driverUsername;
        this.driverPassword = driverPassword;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.dropoffLat = dropoffLat;
        this.dropoffLng = dropoffLng;
        this.offerTimeoutSeconds = offerTimeoutSeconds;
        this.heartbeatWaitSeconds = heartbeatWaitSeconds;
    }
}

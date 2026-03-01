package com.cdwater.ridego.simulator.scenario;

import com.cdwater.ridego.contracts.DriverPositionPayload;
import com.cdwater.ridego.simulator.client.RideGoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

abstract class PreparedScenario implements Scenario {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final RideGoClient client;
    protected final SimulatorSettings settings;
    protected final Clock clock;

    protected PreparedScenario(RideGoClient client, SimulatorSettings settings, Clock clock) {
        this.client = client;
        this.settings = settings;
        this.clock = clock;
    }

    protected Session prepare() throws InterruptedException {
        RideGoClient.Login rider = client.login(settings.riderUsername, settings.riderPassword);
        RideGoClient.Login driver = client.login(settings.driverUsername, settings.driverPassword);

        log.info("driver {} online at city {}", driver.driverId(), settings.cityId);
        client.setPresence(driver.token(), driver.driverId(), settings.cityId);
        publishPosition(driver.driverId());
        Thread.sleep(1500);
        return new Session(rider.token(), driver.token(), driver.driverId());
    }

    protected void publishPosition(String driverId) {
        client.publishPosition(new DriverPositionPayload(
                driverId, settings.cityId,
                settings.pickupLat, settings.pickupLng, clock.instant()));
    }

    protected String createTrip(String riderToken) {
        return client.createTripId(riderToken, new RideGoClient.TripRequest(
                settings.cityId,
                settings.pickupLat, settings.pickupLng,
                settings.dropoffLat, settings.dropoffLng));
    }

    protected record Session(String riderToken, String driverToken, String driverId) {
    }
}

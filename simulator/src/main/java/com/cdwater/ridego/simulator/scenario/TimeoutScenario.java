package com.cdwater.ridego.simulator.scenario;

import com.cdwater.ridego.simulator.client.RideGoClient;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component("timeout")
public class TimeoutScenario extends PreparedScenario {

    public TimeoutScenario(RideGoClient client, SimulatorSettings settings, Clock clock) {
        super(client, settings, clock);
    }

    @Override
    public void run() throws Exception {
        Session session = prepare();
        String tripId = createTrip(session.riderToken());
        String offerId = client.waitForOffer(session.driverToken(), settings.offerTimeoutSeconds);
        log.info("trip {} offered to driver ({}), driver ignores it", tripId, offerId);

        client.waitForState(session.riderToken(), tripId, "unmatched", 90);
        log.info("trip {} unmatched after matching window", tripId);
        log.info("timeout scenario OK");
    }
}

package com.cdwater.ridego.simulator.scenario;

import com.cdwater.ridego.simulator.client.RideGoClient;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component("heartbeat")
public class HeartbeatScenario extends PreparedScenario {

    public HeartbeatScenario(RideGoClient client, SimulatorSettings settings, Clock clock) {
        super(client, settings, clock);
    }

    @Override
    public void run() throws Exception {
        Session session = prepare();
        log.info("driver will stop sending GPS; waiting {}s for heartbeat expiry",
                settings.heartbeatWaitSeconds);
        Thread.sleep(settings.heartbeatWaitSeconds * 1000L);

        String tripId = createTrip(session.riderToken());
        client.waitForState(session.riderToken(), tripId, "unmatched", 90);
        log.info("trip {} unmatched; heartbeat expiry OK", tripId);
    }
}

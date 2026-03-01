package com.cdwater.ridego.simulator.scenario;

import com.cdwater.ridego.simulator.client.RideGoClient;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component("cancel")
public class CancelScenario extends PreparedScenario {

    public CancelScenario(RideGoClient client, SimulatorSettings settings, Clock clock) {
        super(client, settings, clock);
    }

    @Override
    public void run() throws Exception {
        Session session = prepare();
        String tripId = createTrip(session.riderToken());
        String offerId = client.waitForOffer(session.driverToken(), settings.offerTimeoutSeconds);
        client.acceptOffer(session.driverToken(), offerId);
        client.waitForState(session.riderToken(), tripId, "accepted", 20);
        log.info("trip {} accepted, rider cancels", tripId);

        client.cancel(session.riderToken(), tripId, "rider_changed_mind");
        client.waitForState(session.riderToken(), tripId, "cancelled", 20);
        log.info("trip {} cancelled", tripId);

        Thread.sleep(1000);
        String secondTrip = createTrip(session.riderToken());
        String secondOffer = client.waitForOffer(session.driverToken(), settings.offerTimeoutSeconds);
        log.info("driver released and re-offered on trip {} (offer {})", secondTrip, secondOffer);
        client.acceptOffer(session.driverToken(), secondOffer);
        client.cancel(session.riderToken(), secondTrip, "cleanup");
        client.waitForState(session.riderToken(), secondTrip, "cancelled", 20);
        log.info("cancel scenario OK");
    }
}

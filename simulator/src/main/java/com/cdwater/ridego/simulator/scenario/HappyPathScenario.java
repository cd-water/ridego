package com.cdwater.ridego.simulator.scenario;

import com.cdwater.ridego.simulator.client.RideGoClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Clock;

@Component("happy")
public class HappyPathScenario extends PreparedScenario {

    public HappyPathScenario(RideGoClient client, SimulatorSettings settings, Clock clock) {
        super(client, settings, clock);
    }

    @Override
    public void run() throws Exception {
        Session session = prepare();
        String tripId = createTrip(session.riderToken());
        log.info("trip {} requested", tripId);

        String offerId = client.waitForOffer(session.driverToken(), settings.offerTimeoutSeconds);
        log.info("driver accepted offer {}", offerId);
        client.acceptOffer(session.driverToken(), offerId);

        Thread.sleep(1500);
        client.arrive(session.driverToken(), tripId);
        client.start(session.driverToken(), tripId);
        log.info("trip {} in progress", tripId);

        Thread.sleep(1000);
        publishPosition(session.driverId());
        Thread.sleep(500);
        client.complete(session.driverToken(), tripId);

        JsonNode completed = client.waitForState(
                session.riderToken(), tripId, "completed", 20);
        log.info("trip {} completed: {}", tripId, completed);

        JsonNode invoice = client.waitForInvoice(session.riderToken(), tripId);
        log.info("invoice for trip {}: amount={} state={}",
                tripId, invoice.get("amountCents").asInt(), invoice.get("state").asText());
        log.info("happy path OK");
    }
}

package com.cdwater.ridego.simulator;

import com.cdwater.ridego.simulator.scenario.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SimulatorRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulatorRunner.class);

    private final Map<String, Scenario> scenarios;

    @Value("${ridego.simulator.scenario:happy}")
    private String scenario;

    public SimulatorRunner(Map<String, Scenario> scenarios) {
        this.scenarios = scenarios;
    }

    @Override
    public void run(ApplicationArguments args) {
        Scenario selected = scenarios.get(scenario);
        if (selected == null) {
            log.warn("unknown scenario '{}', falling back to happy", scenario);
            selected = scenarios.get("happy");
        }
        try {
            selected.run();
            System.exit(0);
        } catch (Exception e) {
            log.error("simulation failed", e);
            System.exit(1);
        }
    }
}

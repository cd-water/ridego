package com.cdwater.ridego.dispatch.persistence;

import com.cdwater.ridego.dispatch.availability.AvailabilityRepository;
import com.cdwater.ridego.dispatch.lifecycle.DriverAvailability;
import org.springframework.stereotype.Component;

@Component
public class RepositoryDriverAvailability implements DriverAvailability {

    private final AvailabilityRepository availability;

    public RepositoryDriverAvailability(AvailabilityRepository availability) {
        this.availability = availability;
    }

    @Override
    public boolean isIdle(String driverId, String cityId) {
        return availability.isIdle(driverId, cityId);
    }

    @Override
    public void markOffered(String driverId) {
        availability.move(driverId, "IDLE", "OFFERED");
    }

    @Override
    public void markOnTrip(String driverId) {
        availability.move(driverId, "OFFERED", "ON_TRIP");
    }

    @Override
    public void release(String driverId) {
        availability.release(driverId);
    }
}

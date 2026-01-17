package com.cdwater.ridego.trip.trip;

import com.cdwater.ridego.auth.Principal;
import com.cdwater.ridego.contracts.Roles;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse create(Principal principal, @RequestBody CreateTripRequest request) {
        principal.requireRole(Roles.RIDER);
        return tripService.create(principal.userId(), request);
    }

    @GetMapping("/{id}")
    public TripView get(Principal principal, @PathVariable UUID id) {
        return tripService.get(principal.userId(), principal.driverId(), id);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(Principal principal,
                       @PathVariable UUID id,
                       @RequestBody(required = false) CancelTripRequest request) {
        String reason = request == null ? null : request.reason();
        tripService.cancel(id, principal.userId(), principal.driverId(), reason);
    }

    @PostMapping("/{id}/arrive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void arrive(Principal principal, @PathVariable UUID id) {
        principal.requireRole(Roles.DRIVER);
        tripService.arrive(id, principal.requireDriverId());
    }

    @PostMapping("/{id}/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void start(Principal principal, @PathVariable UUID id) {
        principal.requireRole(Roles.DRIVER);
        tripService.start(id, principal.requireDriverId());
    }

    @PostMapping("/{id}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(Principal principal, @PathVariable UUID id) {
        principal.requireRole(Roles.DRIVER);
        tripService.complete(id, principal.requireDriverId());
    }
}

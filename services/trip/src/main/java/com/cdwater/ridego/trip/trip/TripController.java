package com.cdwater.ridego.trip.trip;

import com.cdwater.ridego.contracts.InternalHeaders;
import com.cdwater.ridego.contracts.Roles;
import com.cdwater.ridego.trip.web.ForbiddenException;
import com.cdwater.ridego.trip.web.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public TripResponse create(
            @RequestHeader(name = InternalHeaders.USER_ID, required = false) String userId,
            @RequestHeader(name = InternalHeaders.ROLE, required = false) String role,
            @RequestBody CreateTripRequest request) {
        if (userId == null) {
            throw new UnauthorizedException();
        }
        if (!Roles.RIDER.equals(role)) {
            throw new ForbiddenException();
        }
        return tripService.create(userId, request);
    }

    @GetMapping("/{id}")
    public TripView get(
            @RequestHeader(name = InternalHeaders.USER_ID, required = false) String userId,
            @PathVariable UUID id) {
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return tripService.get(userId, id);
    }
}

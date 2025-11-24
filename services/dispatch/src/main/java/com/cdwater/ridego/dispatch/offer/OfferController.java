package com.cdwater.ridego.dispatch.offer;

import com.cdwater.ridego.contracts.InternalHeaders;
import com.cdwater.ridego.contracts.Roles;
import com.cdwater.ridego.dispatch.web.ForbiddenException;
import com.cdwater.ridego.dispatch.web.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/offers")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @GetMapping("/pending")
    public List<PendingOffer> pending(
            @RequestHeader(name = InternalHeaders.DRIVER_ID, required = false) String driverId,
            @RequestHeader(name = InternalHeaders.ROLE, required = false) String role) {
        requireDriver(driverId, role);
        return offerService.pendingForDriver(driverId).stream()
                .map(offer -> new PendingOffer(offer.id().toString(), offer.tripId().toString(), offer.expiresAt()))
                .toList();
    }

    @PostMapping("/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable UUID id,
                       @RequestHeader(name = InternalHeaders.DRIVER_ID, required = false) String driverId,
                       @RequestHeader(name = InternalHeaders.ROLE, required = false) String role) {
        requireDriver(driverId, role);
        offerService.accept(id, driverId);
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID id,
                       @RequestHeader(name = InternalHeaders.DRIVER_ID, required = false) String driverId,
                       @RequestHeader(name = InternalHeaders.ROLE, required = false) String role) {
        requireDriver(driverId, role);
        offerService.reject(id, driverId);
    }

    private void requireDriver(String driverId, String role) {
        if (driverId == null) {
            throw new UnauthorizedException();
        }
        if (!Roles.DRIVER.equals(role)) {
            throw new ForbiddenException();
        }
    }

    public record PendingOffer(String offerId, String tripId, Instant expiresAt) {
    }
}

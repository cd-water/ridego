package com.cdwater.ridego.dispatch.offer;

import com.cdwater.ridego.contracts.InternalHeaders;
import com.cdwater.ridego.contracts.Roles;
import com.cdwater.ridego.dispatch.lifecycle.OfferLifecycle;
import com.cdwater.ridego.dispatch.lifecycle.OfferStore;
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

    private final OfferLifecycle offerLifecycle;
    private final OfferStore offerStore;

    public OfferController(OfferLifecycle offerLifecycle, OfferStore offerStore) {
        this.offerLifecycle = offerLifecycle;
        this.offerStore = offerStore;
    }

    @GetMapping("/pending")
    public List<PendingOffer> pending(
            @RequestHeader(name = InternalHeaders.DRIVER_ID, required = false) String driverId,
            @RequestHeader(name = InternalHeaders.ROLE, required = false) String role) {
        requireDriver(driverId, role);
        return offerStore.pendingForDriver(driverId).stream()
                .map(offer -> new PendingOffer(
                        offer.getId().toString(),
                        offer.getTripId().toString(),
                        offer.getExpiresAt()))
                .toList();
    }

    @PostMapping("/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable UUID id,
                       @RequestHeader(name = InternalHeaders.DRIVER_ID, required = false) String driverId,
                       @RequestHeader(name = InternalHeaders.ROLE, required = false) String role) {
        requireDriver(driverId, role);
        offerLifecycle.accept(id, driverId);
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID id,
                       @RequestHeader(name = InternalHeaders.DRIVER_ID, required = false) String driverId,
                       @RequestHeader(name = InternalHeaders.ROLE, required = false) String role) {
        requireDriver(driverId, role);
        offerLifecycle.reject(id, driverId);
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

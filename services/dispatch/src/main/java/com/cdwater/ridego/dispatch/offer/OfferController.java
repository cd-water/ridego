package com.cdwater.ridego.dispatch.offer;

import com.cdwater.ridego.auth.Principal;
import com.cdwater.ridego.contracts.Roles;
import com.cdwater.ridego.dispatch.lifecycle.OfferLifecycle;
import com.cdwater.ridego.dispatch.lifecycle.OfferStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    public List<PendingOffer> pending(Principal principal) {
        String driverId = requireDriver(principal);
        return offerStore.pendingForDriver(driverId).stream()
                .map(offer -> new PendingOffer(
                        offer.getId().toString(),
                        offer.getTripId().toString(),
                        offer.getExpiresAt()))
                .toList();
    }

    @PostMapping("/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(Principal principal, @PathVariable UUID id) {
        offerLifecycle.accept(id, requireDriver(principal));
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(Principal principal, @PathVariable UUID id) {
        offerLifecycle.reject(id, requireDriver(principal));
    }

    private String requireDriver(Principal principal) {
        principal.requireRole(Roles.DRIVER);
        return principal.requireDriverId();
    }

    public record PendingOffer(String offerId, String tripId, Instant expiresAt) {
    }
}

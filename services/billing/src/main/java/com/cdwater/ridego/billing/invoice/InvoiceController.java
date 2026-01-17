package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.auth.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/{tripId}")
    public InvoiceResponse get(Principal principal, @PathVariable UUID tripId) {
        InvoiceService.Invoice invoice = invoiceService.findByTripId(tripId, principal.userId());
        return new InvoiceResponse(
                invoice.id().toString(),
                invoice.tripId().toString(),
                invoice.amountCents(),
                invoice.state());
    }

    public record InvoiceResponse(String invoiceId, String tripId, int amountCents, String state) {
    }
}

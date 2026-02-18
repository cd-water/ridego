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

    private final InvoiceModule invoiceModule;

    public InvoiceController(InvoiceModule invoiceModule) {
        this.invoiceModule = invoiceModule;
    }

    @GetMapping("/{tripId}")
    public InvoiceResponse get(Principal principal, @PathVariable UUID tripId) {
        Invoice invoice = invoiceModule.findForRider(tripId, principal.userId())
                .orElseThrow(InvoiceNotFoundException::new);
        return new InvoiceResponse(
                invoice.getId().toString(),
                invoice.getTripId().toString(),
                invoice.getAmountCents(),
                invoice.getState().wireValue());
    }

    public record InvoiceResponse(String invoiceId, String tripId, int amountCents, String state) {
    }
}

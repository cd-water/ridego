package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.contracts.InternalHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public InvoiceResponse get(@PathVariable UUID tripId,
                               @RequestHeader(name = InternalHeaders.USER_ID, required = false) String userId) {
        if (userId == null) {
            throw new InvoiceNotFoundException();
        }
        InvoiceService.Invoice invoice = invoiceService.findByTripId(tripId, userId);
        return new InvoiceResponse(
                invoice.id().toString(),
                invoice.tripId().toString(),
                invoice.amountCents(),
                invoice.state());
    }

    public record InvoiceResponse(String invoiceId, String tripId, int amountCents, String state) {
    }
}

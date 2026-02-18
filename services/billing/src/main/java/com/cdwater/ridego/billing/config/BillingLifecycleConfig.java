package com.cdwater.ridego.billing.config;

import com.cdwater.ridego.billing.invoice.DedupStore;
import com.cdwater.ridego.billing.invoice.FarePolicy;
import com.cdwater.ridego.billing.invoice.InvoiceEventSink;
import com.cdwater.ridego.billing.invoice.InvoiceModule;
import com.cdwater.ridego.billing.invoice.InvoiceStore;
import com.cdwater.ridego.billing.invoice.MetricsInvoiceEventSink;
import com.cdwater.ridego.billing.invoice.OutboxInvoiceEventSink;
import com.cdwater.ridego.billing.invoice.ZeroFarePolicy;
import com.cdwater.ridego.messaging.OutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BillingLifecycleConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public FarePolicy farePolicy() {
        return new ZeroFarePolicy();
    }

    @Bean
    public InvoiceEventSink invoiceEventSink(OutboxRepository outbox,
                                             MeterRegistry meterRegistry) {
        return new MetricsInvoiceEventSink(new OutboxInvoiceEventSink(outbox), meterRegistry);
    }

    @Bean
    public InvoiceModule invoiceModule(InvoiceStore store,
                                       InvoiceEventSink events,
                                       DedupStore dedup,
                                       FarePolicy farePolicy,
                                       Clock clock) {
        return new InvoiceModule(store, events, dedup, farePolicy, clock);
    }
}

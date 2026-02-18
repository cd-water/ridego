package com.cdwater.ridego.billing.invoice;

import com.cdwater.ridego.messaging.ProcessedEvents;
import org.springframework.stereotype.Component;

@Component
public class ProcessedEventDedup implements DedupStore {

    private final ProcessedEvents processedEvents;

    public ProcessedEventDedup(ProcessedEvents processedEvents) {
        this.processedEvents = processedEvents;
    }

    @Override
    public boolean markOnce(String consumer, String eventId) {
        return processedEvents.markOnce(consumer, eventId);
    }
}

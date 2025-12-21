package com.cdwater.ridego.messaging;

import com.cdwater.ridego.contracts.EventEnvelopeCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration
public class MessagingConfiguration {

    @Bean
    public EventEnvelopeCodec eventEnvelopeCodec(ObjectMapper objectMapper,
                                                  ObjectProvider<Clock> clock) {
        return new EventEnvelopeCodec(objectMapper, clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    public OutboxRepository outboxRepository(JdbcTemplate jdbcTemplate,
                                             EventEnvelopeCodec codec) {
        return new OutboxRepository(jdbcTemplate, codec);
    }

    @Bean
    public OutboxRelay outboxRelay(OutboxRepository outbox,
                                   KafkaTemplate<String, String> kafkaTemplate) {
        return new OutboxRelay(outbox, kafkaTemplate);
    }

    @Bean
    public ProcessedEvents processedEvents(JdbcTemplate jdbcTemplate) {
        return new ProcessedEvents(jdbcTemplate);
    }
}

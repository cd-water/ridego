package com.cdwater.ridego.trip.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    public static final String TRIP_EVENTS = "trip.events.v1";

    @Bean
    public NewTopic tripEventsTopic() {
        return TopicBuilder.name(TRIP_EVENTS).partitions(12).replicas(1).build();
    }
}

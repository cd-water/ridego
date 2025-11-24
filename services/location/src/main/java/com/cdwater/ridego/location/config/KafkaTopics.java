package com.cdwater.ridego.location.config;

import com.cdwater.ridego.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    @Bean
    public NewTopic driverPositionsTopic() {
        return TopicBuilder.name(Topics.DRIVER_POSITIONS).partitions(24).replicas(1).build();
    }

    @Bean
    public NewTopic locationEventsTopic() {
        return TopicBuilder.name(Topics.LOCATION_EVENTS).partitions(12).replicas(1).build();
    }
}

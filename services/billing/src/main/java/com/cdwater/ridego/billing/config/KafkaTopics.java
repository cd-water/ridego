package com.cdwater.ridego.billing.config;

import com.cdwater.ridego.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    @Bean
    public NewTopic billingEventsTopic() {
        return TopicBuilder.name(Topics.BILLING_EVENTS).partitions(6).replicas(1).build();
    }
}

package com.cdwater.ridego.dispatch.config;

import com.cdwater.ridego.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    @Bean
    public NewTopic dispatchEventsTopic() {
        return TopicBuilder.name(Topics.DISPATCH_EVENTS).partitions(12).replicas(1).build();
    }
}

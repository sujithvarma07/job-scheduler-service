package com.sujith.scheduler.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String JOB_CREATED_TOPIC = "job.created";
    public static final String JOB_STARTED_TOPIC = "job.started";
    public static final String JOB_COMPLETED_TOPIC = "job.completed";
    public static final String JOB_FAILED_TOPIC = "job.failed";

    @Bean
    public NewTopic jobCreatedTopic() {
        return TopicBuilder.name(JOB_CREATED_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic jobStartedTopic() {
        return TopicBuilder.name(JOB_STARTED_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic jobCompletedTopic() {
        return TopicBuilder.name(JOB_COMPLETED_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic jobFailedTopic() {
        return TopicBuilder.name(JOB_FAILED_TOPIC).partitions(3).replicas(1).build();
    }
}

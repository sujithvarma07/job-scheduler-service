package com.sujith.scheduler.service;

import com.sujith.scheduler.config.KafkaConfig;
import com.sujith.scheduler.event.JobEvent;
import com.sujith.scheduler.model.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobEventProducer {

    private final KafkaTemplate<String, JobEvent> kafkaTemplate;

    public void publishJobCreated(Job job) {
        publish(KafkaConfig.JOB_CREATED_TOPIC, toEvent(job));
    }

    public void publishJobStarted(Job job) {
        publish(KafkaConfig.JOB_STARTED_TOPIC, toEvent(job));
    }

    public void publishJobCompleted(Job job) {
        publish(KafkaConfig.JOB_COMPLETED_TOPIC, toEvent(job));
    }

    public void publishJobFailed(Job job) {
        publish(KafkaConfig.JOB_FAILED_TOPIC, toEvent(job));
    }

    private JobEvent toEvent(Job job) {
        return JobEvent.builder()
                .jobId(job.getId())
                .jobName(job.getName())
                .status(job.getStatus())
                .timestamp(Instant.now())
                .errorMessage(job.getErrorMessage())
                .build();
    }

    private void publish(String topic, JobEvent event) {
        kafkaTemplate.send(topic, event.getJobId().toString(), event);
        log.debug("published event for job {} with status {} to topic {}", event.getJobId(), event.getStatus(), topic);
    }
}

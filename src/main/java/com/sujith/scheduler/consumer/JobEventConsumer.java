package com.sujith.scheduler.consumer;

import com.sujith.scheduler.config.KafkaConfig;
import com.sujith.scheduler.event.JobEvent;
import com.sujith.scheduler.model.JobAuditLog;
import com.sujith.scheduler.repository.JobAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobEventConsumer {

    private final JobAuditLogRepository jobAuditLogRepository;

    @KafkaListener(
            topics = {
                    KafkaConfig.JOB_CREATED_TOPIC,
                    KafkaConfig.JOB_STARTED_TOPIC,
                    KafkaConfig.JOB_COMPLETED_TOPIC,
                    KafkaConfig.JOB_FAILED_TOPIC
            },
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onJobEvent(JobEvent event) {
        log.info("job event received: jobId={}, jobName={}, status={}, timestamp={}",
                event.getJobId(), event.getJobName(), event.getStatus(), event.getTimestamp());
        persistAuditLog(event);
    }

    private void persistAuditLog(JobEvent event) {
        JobAuditLog auditLog = JobAuditLog.builder()
                .jobId(event.getJobId())
                .status(event.getStatus())
                .message(event.getErrorMessage())
                .timestamp(event.getTimestamp())
                .build();
        jobAuditLogRepository.save(auditLog);
        log.debug("persisted audit log entry for job {}", event.getJobId());
    }
}

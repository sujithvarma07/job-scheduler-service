package com.sujith.scheduler.service;

import com.sujith.scheduler.model.Job;
import com.sujith.scheduler.model.JobStatus;
import com.sujith.scheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Periodically checks completed jobs that carry a cron expression and re-submits a new
 * instance of the job once the expression's next fire time has passed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CronSchedulerService {

    private final JobRepository jobRepository;
    private final JobQueueService jobQueueService;

    @Scheduled(fixedDelay = 60000)
    public void scheduleRecurringJobs() {
        List<Job> recurringJobs = jobRepository.findByCronExpressionIsNotNullAndStatus(JobStatus.COMPLETED);
        for (Job job : recurringJobs) {
            try {
                resubmitIfDue(job);
            } catch (Exception e) {
                log.error("failed to evaluate cron schedule for job {}: {}", job.getId(), e.getMessage());
            }
        }
    }

    private void resubmitIfDue(Job job) {
        CronExpression cron = CronExpression.parse(job.getCronExpression());
        Instant reference = job.getCompletedAt() != null ? job.getCompletedAt() : job.getCreatedAt();
        ZonedDateTime nextFireTime = cron.next(ZonedDateTime.ofInstant(reference, ZoneOffset.UTC));
        if (nextFireTime == null || nextFireTime.toInstant().isAfter(Instant.now())) {
            return;
        }

        Job recurrence = Job.builder()
                .name(job.getName())
                .payload(job.getPayload())
                .status(JobStatus.QUEUED)
                .priority(job.getPriority())
                .maxRetries(job.getMaxRetries())
                .timeoutSeconds(job.getTimeoutSeconds())
                .cronExpression(job.getCronExpression())
                .build();
        Job saved = jobRepository.save(recurrence);
        jobQueueService.enqueue(saved);
        log.info("resubmitted recurring job {} as new instance {} based on cron '{}'",
                job.getId(), saved.getId(), job.getCronExpression());

        // Advance the reference point so this completed job isn't evaluated again
        // until its next fire time.
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
    }
}

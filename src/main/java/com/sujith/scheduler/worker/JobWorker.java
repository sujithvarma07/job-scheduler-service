package com.sujith.scheduler.worker;

import com.sujith.scheduler.model.Job;
import com.sujith.scheduler.model.JobStatus;
import com.sujith.scheduler.repository.JobRepository;
import com.sujith.scheduler.service.DistributedLockService;
import com.sujith.scheduler.service.JobQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobWorker {

    private static final String LOCK_PREFIX = "job:lock:";
    private static final long LOCK_TTL_SECONDS = 60;

    private final JobQueueService jobQueueService;
    private final DistributedLockService distributedLockService;
    private final JobRepository jobRepository;

    @Scheduled(fixedDelayString = "${scheduler.queue.poll-interval-ms}")
    public void pollAndExecute() {
        Optional<UUID> next = jobQueueService.dequeue();
        if (next.isEmpty()) {
            return;
        }

        UUID jobId = next.get();
        String lockKey = LOCK_PREFIX + jobId;
        if (!distributedLockService.acquireLock(lockKey, LOCK_TTL_SECONDS)) {
            log.debug("could not acquire lock for job {}, skipping this cycle", jobId);
            return;
        }

        try {
            executeJob(jobId);
        } finally {
            distributedLockService.releaseLock(lockKey);
        }
    }

    private void executeJob(UUID jobId) {
        Optional<Job> maybeJob = jobRepository.findById(jobId);
        if (maybeJob.isEmpty()) {
            log.warn("job {} not found, skipping", jobId);
            return;
        }

        Job job = maybeJob.get();
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        jobRepository.save(job);
        log.info("started job {} ({})", job.getId(), job.getName());

        try {
            // simulated execution until real job handlers are wired up
            Thread.sleep(1000);

            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
            log.info("completed job {}", job.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("execution of job {} was interrupted", job.getId());
        }
    }
}

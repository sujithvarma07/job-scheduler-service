package com.sujith.scheduler.worker;

import com.sujith.scheduler.metrics.JobMetrics;
import com.sujith.scheduler.model.Job;
import com.sujith.scheduler.model.JobStatus;
import com.sujith.scheduler.repository.JobRepository;
import com.sujith.scheduler.service.DistributedLockService;
import com.sujith.scheduler.service.JobEventProducer;
import com.sujith.scheduler.service.JobQueueService;
import com.sujith.scheduler.util.RetryUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
public class JobWorker {

    private static final String LOCK_PREFIX = "job:lock:";
    private static final long LOCK_TTL_SECONDS = 60;
    private static final double DEPENDENCY_RECHECK_DELAY_MILLIS = 5000;

    private final JobQueueService jobQueueService;
    private final DistributedLockService distributedLockService;
    private final JobRepository jobRepository;
    private final JobEventProducer jobEventProducer;
    private final JobMetrics jobMetrics;
    private final ThreadPoolTaskExecutor jobExecutor;
    private final Semaphore concurrencyLimit;

    public JobWorker(JobQueueService jobQueueService,
                     DistributedLockService distributedLockService,
                     JobRepository jobRepository,
                     JobEventProducer jobEventProducer,
                     JobMetrics jobMetrics,
                     @Qualifier("jobExecutor") ThreadPoolTaskExecutor jobExecutor,
                     @Value("${scheduler.worker.pool-size}") int poolSize) {
        this.jobQueueService = jobQueueService;
        this.distributedLockService = distributedLockService;
        this.jobRepository = jobRepository;
        this.jobEventProducer = jobEventProducer;
        this.jobMetrics = jobMetrics;
        this.jobExecutor = jobExecutor;
        this.concurrencyLimit = new Semaphore(poolSize);
    }

    /**
     * Dequeues as many jobs as there are free worker slots and hands each one to the
     * job executor. The semaphore caps in-flight executions at the configured pool size,
     * so the poller never outruns the thread pool.
     */
    @Scheduled(fixedDelayString = "${scheduler.queue.poll-interval-ms}")
    public void pollAndExecute() {
        while (concurrencyLimit.tryAcquire()) {
            Optional<UUID> next = jobQueueService.dequeue();
            if (next.isEmpty()) {
                concurrencyLimit.release();
                return;
            }

            UUID jobId = next.get();
            String lockKey = LOCK_PREFIX + jobId;
            if (!distributedLockService.acquireLock(lockKey, LOCK_TTL_SECONDS)) {
                log.debug("could not acquire lock for job {}, skipping", jobId);
                concurrencyLimit.release();
                continue;
            }

            dispatch(jobId, lockKey);
        }
    }

    private void dispatch(UUID jobId, String lockKey) {
        jobMetrics.incrementActiveWorkers();
        try {
            CompletableFuture.supplyAsync(() -> {
                executeJob(jobId);
                return jobId;
            }, jobExecutor).whenComplete((id, error) -> {
                if (error != null) {
                    log.error("unexpected error while executing job {}: {}", jobId, error.getMessage());
                }
                releaseSlot(lockKey);
            });
        } catch (TaskRejectedException e) {
            log.warn("executor rejected job {}, returning it to the queue", jobId);
            jobRepository.findById(jobId).ifPresent(jobQueueService::enqueue);
            releaseSlot(lockKey);
        }
    }

    private void releaseSlot(String lockKey) {
        try {
            distributedLockService.releaseLock(lockKey);
        } finally {
            jobMetrics.decrementActiveWorkers();
            concurrencyLimit.release();
        }
    }

    private void executeJob(UUID jobId) {
        Optional<Job> maybeJob = jobRepository.findById(jobId);
        if (maybeJob.isEmpty()) {
            log.warn("job {} not found, skipping", jobId);
            return;
        }

        Job job = maybeJob.get();
        if (job.getStatus() == JobStatus.CANCELLED) {
            log.info("job {} was cancelled, skipping execution", job.getId());
            return;
        }

        if (isWaitingOnDependency(job)) {
            jobQueueService.enqueueWithDelay(job, DEPENDENCY_RECHECK_DELAY_MILLIS);
            log.debug("job {} is waiting on dependency {}, re-enqueued with delay",
                    job.getId(), job.getDependsOnJobId());
            return;
        }

        Instant startTime = Instant.now();
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(startTime);
        jobRepository.save(job);
        jobEventProducer.publishJobStarted(job);
        log.info("started job {} ({})", job.getId(), job.getName());

        try {
            // simulated execution until real job handlers are wired up
            Thread.sleep(1000);

            long elapsedSeconds = Instant.now().getEpochSecond() - startTime.getEpochSecond();
            if (elapsedSeconds > job.getTimeoutSeconds()) {
                handleTimeout(job);
                return;
            }

            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
            jobEventProducer.publishJobCompleted(job);
            jobMetrics.incrementCompleted();
            jobMetrics.recordExecutionTime(Duration.between(startTime, job.getCompletedAt()).toMillis());
            log.info("completed job {}", job.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("execution of job {} was interrupted", job.getId());
        } catch (Exception e) {
            handleFailure(job, e);
        }
    }

    private boolean isWaitingOnDependency(Job job) {
        if (job.getDependsOnJobId() == null) {
            return false;
        }
        return jobRepository.findById(job.getDependsOnJobId())
                .map(dependency -> dependency.getStatus() != JobStatus.COMPLETED)
                .orElse(false);
    }

    private void handleTimeout(Job job) {
        job.setStatus(JobStatus.FAILED);
        job.setErrorMessage("job timed out");
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
        jobEventProducer.publishJobFailed(job);
        jobMetrics.incrementFailed();
        jobMetrics.recordExecutionTime(Duration.between(job.getStartedAt(), job.getCompletedAt()).toMillis());
        log.warn("job {} timed out after {} seconds", job.getId(), job.getTimeoutSeconds());
    }

    private void handleFailure(Job job, Exception e) {
        log.error("execution of job {} failed: {}", job.getId(), e.getMessage());
        jobMetrics.incrementFailed();
        jobMetrics.recordExecutionTime(Duration.between(job.getStartedAt(), Instant.now()).toMillis());

        if (job.getRetryCount() < job.getMaxRetries()) {
            job.setRetryCount(job.getRetryCount() + 1);
            job.setStatus(JobStatus.QUEUED);
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);

            double backoffMillis = RetryUtil.calculateBackoffScore(job.getRetryCount());
            jobQueueService.enqueueWithDelay(job, backoffMillis);
            jobEventProducer.publishJobFailed(job);
            log.info("scheduled retry {}/{} for job {} with backoff {}ms",
                    job.getRetryCount(), job.getMaxRetries(), job.getId(), backoffMillis);
        } else {
            job.setStatus(JobStatus.DEAD_LETTER);
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
            jobEventProducer.publishJobFailed(job);
            log.warn("job {} moved to dead letter queue after {} retries", job.getId(), job.getRetryCount());
        }
    }
}

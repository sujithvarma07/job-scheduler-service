package com.sujith.scheduler.metrics;

import com.sujith.scheduler.service.JobQueueService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Central registration point for job throughput and queue depth metrics, exposed
 * to Prometheus via the actuator endpoint.
 */
@Component
public class JobMetrics {

    private final MeterRegistry meterRegistry;
    private final JobQueueService jobQueueService;

    private Counter jobsSubmittedCounter;
    private Counter jobsCompletedCounter;
    private Counter jobsFailedCounter;
    private Timer jobsExecutionTimer;

    public JobMetrics(MeterRegistry meterRegistry, JobQueueService jobQueueService) {
        this.meterRegistry = meterRegistry;
        this.jobQueueService = jobQueueService;
    }

    @PostConstruct
    public void registerMetrics() {
        jobsSubmittedCounter = Counter.builder("jobs.submitted")
                .description("total number of jobs submitted for execution")
                .register(meterRegistry);

        jobsCompletedCounter = Counter.builder("jobs.completed")
                .description("total number of jobs completed successfully")
                .register(meterRegistry);

        jobsFailedCounter = Counter.builder("jobs.failed")
                .description("total number of job execution failures, including retries and dead letters")
                .register(meterRegistry);

        jobsExecutionTimer = Timer.builder("jobs.execution.duration")
                .description("time taken to execute a job, from start to completion or failure")
                .register(meterRegistry);

        meterRegistry.gauge("jobs.queue.size", jobQueueService, JobQueueService::queueSize);
    }

    public void incrementSubmitted() {
        jobsSubmittedCounter.increment();
    }

    public void incrementCompleted() {
        jobsCompletedCounter.increment();
    }

    public void incrementFailed() {
        jobsFailedCounter.increment();
    }

    public void recordExecutionTime(long durationMillis) {
        jobsExecutionTimer.record(durationMillis, TimeUnit.MILLISECONDS);
    }
}

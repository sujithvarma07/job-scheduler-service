package com.sujith.scheduler.service;

import com.sujith.scheduler.dto.BatchJobRequest;
import com.sujith.scheduler.dto.BatchJobResponse;
import com.sujith.scheduler.dto.JobRequest;
import com.sujith.scheduler.dto.JobResponse;
import com.sujith.scheduler.exception.InvalidJobStateException;
import com.sujith.scheduler.exception.JobNotFoundException;
import com.sujith.scheduler.mapper.JobMapper;
import com.sujith.scheduler.metrics.JobMetrics;
import com.sujith.scheduler.model.Job;
import com.sujith.scheduler.model.JobStatus;
import com.sujith.scheduler.repository.JobRepository;
import com.sujith.scheduler.repository.JobSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private static final List<JobStatus> TERMINAL_STATUSES = List.of(
            JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED, JobStatus.DEAD_LETTER);

    private final JobRepository jobRepository;
    private final JobQueueService jobQueueService;
    private final JobEventProducer jobEventProducer;
    private final JobMetrics jobMetrics;

    public JobResponse submitJob(JobRequest request) {
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<Job> existing = jobRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("job with idempotency key {} already exists, returning existing job {}",
                        request.getIdempotencyKey(), existing.get().getId());
                return JobMapper.toResponse(existing.get());
            }
        }

        Job job = JobMapper.toEntity(request);
        job.setStatus(JobStatus.PENDING);
        Job saved = jobRepository.save(job);
        saved.setStatus(JobStatus.QUEUED);
        saved = jobRepository.save(saved);
        jobQueueService.enqueue(saved);
        jobEventProducer.publishJobCreated(saved);
        jobMetrics.incrementSubmitted();
        log.info("submitted job {} with status {}", saved.getId(), saved.getStatus());
        return JobMapper.toResponse(saved);
    }

    public BatchJobResponse submitBatch(BatchJobRequest batchRequest) {
        List<JobResponse> submitted = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<JobRequest> jobs = batchRequest.getJobs();
        for (int i = 0; i < jobs.size(); i++) {
            JobRequest jobRequest = jobs.get(i);
            try {
                submitted.add(submitJob(jobRequest));
            } catch (Exception e) {
                log.warn("failed to submit job at batch index {}: {}", i, e.getMessage());
                errors.add("job at index " + i + " (" + jobRequest.getName() + "): " + e.getMessage());
            }
        }
        log.info("processed batch of {} jobs: {} submitted, {} failed", jobs.size(), submitted.size(), errors.size());
        return BatchJobResponse.builder()
                .submitted(submitted)
                .errors(errors)
                .build();
    }

    public JobResponse getJob(UUID id) {
        Job job = findJobOrThrow(id);
        return JobMapper.toResponse(job);
    }

    public void cancelJob(UUID id) {
        Job job = findJobOrThrow(id);
        if (job.getStatus() == JobStatus.RUNNING) {
            throw new InvalidJobStateException("cannot cancel job " + id + " because it is currently running");
        }
        if (TERMINAL_STATUSES.contains(job.getStatus())) {
            throw new InvalidJobStateException("cannot cancel job " + id + " because it is already in terminal status " + job.getStatus());
        }
        job.setStatus(JobStatus.CANCELLED);
        jobRepository.save(job);
        log.info("cancelled job {}", id);
    }

    public Page<JobResponse> listJobs(JobStatus status, Pageable pageable) {
        Page<Job> jobs = status == null
                ? jobRepository.findAll(pageable)
                : jobRepository.findByStatus(status, pageable);
        return jobs.map(JobMapper::toResponse);
    }

    public Page<JobResponse> listJobs(JobStatus status,
                                       Integer minPriority,
                                       Integer maxPriority,
                                       Instant from,
                                       Instant to,
                                       Pageable pageable) {
        if (minPriority == null && maxPriority == null && from == null && to == null) {
            return listJobs(status, pageable);
        }
        var spec = JobSpecification.withFilters(status, minPriority, maxPriority, from, to);
        return jobRepository.findAll(spec, pageable).map(JobMapper::toResponse);
    }

    public Page<JobResponse> listDeadLetterJobs(Pageable pageable) {
        return jobRepository.findByStatus(JobStatus.DEAD_LETTER, pageable).map(JobMapper::toResponse);
    }

    public Map<JobStatus, Long> getJobStats() {
        Map<JobStatus, Long> stats = new EnumMap<>(JobStatus.class);
        for (Object[] row : jobRepository.countJobsGroupedByStatus()) {
            stats.put((JobStatus) row[0], (Long) row[1]);
        }
        return stats;
    }

    public JobResponse requeueJob(UUID id) {
        Job job = findJobOrThrow(id);
        if (job.getStatus() != JobStatus.DEAD_LETTER) {
            throw new InvalidJobStateException("cannot requeue job " + id + " because it is not in dead letter status, current status is " + job.getStatus());
        }
        job.setRetryCount(0);
        job.setErrorMessage(null);
        job.setStatus(JobStatus.QUEUED);
        Job saved = jobRepository.save(job);
        jobQueueService.enqueue(saved);
        log.info("requeued dead letter job {}", id);
        return JobMapper.toResponse(saved);
    }

    private Job findJobOrThrow(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));
    }
}

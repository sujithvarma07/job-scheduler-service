package com.sujith.scheduler.service;

import com.sujith.scheduler.dto.JobRequest;
import com.sujith.scheduler.dto.JobResponse;
import com.sujith.scheduler.exception.InvalidJobStateException;
import com.sujith.scheduler.exception.JobNotFoundException;
import com.sujith.scheduler.mapper.JobMapper;
import com.sujith.scheduler.model.Job;
import com.sujith.scheduler.model.JobStatus;
import com.sujith.scheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private static final List<JobStatus> TERMINAL_STATUSES = List.of(
            JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED, JobStatus.DEAD_LETTER);

    private final JobRepository jobRepository;

    public JobResponse submitJob(JobRequest request) {
        Job job = JobMapper.toEntity(request);
        job.setStatus(JobStatus.PENDING);
        Job saved = jobRepository.save(job);
        log.info("submitted job {} with status {}", saved.getId(), saved.getStatus());
        return JobMapper.toResponse(saved);
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

    private Job findJobOrThrow(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));
    }
}

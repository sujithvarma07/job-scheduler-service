package com.sujith.scheduler.mapper;

import com.sujith.scheduler.dto.JobRequest;
import com.sujith.scheduler.dto.JobResponse;
import com.sujith.scheduler.model.Job;
import com.sujith.scheduler.model.JobStatus;

public final class JobMapper {

    private JobMapper() {
    }

    public static JobResponse toResponse(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .name(job.getName())
                .payload(job.getPayload())
                .status(job.getStatus())
                .priority(job.getPriority())
                .maxRetries(job.getMaxRetries())
                .retryCount(job.getRetryCount())
                .scheduledAt(job.getScheduledAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .createdAt(job.getCreatedAt())
                .errorMessage(job.getErrorMessage())
                .build();
    }

    public static Job toEntity(JobRequest request) {
        return Job.builder()
                .name(request.getName())
                .payload(request.getPayload())
                .status(JobStatus.PENDING)
                .priority(request.getPriority())
                .maxRetries(request.getMaxRetries())
                .scheduledAt(request.getScheduledAt())
                .build();
    }
}

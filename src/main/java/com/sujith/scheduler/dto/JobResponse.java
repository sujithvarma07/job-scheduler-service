package com.sujith.scheduler.dto;

import com.sujith.scheduler.model.JobStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class JobResponse {

    private UUID id;

    private String name;

    private String payload;

    private JobStatus status;

    private int priority;

    private int maxRetries;

    private int retryCount;

    private int timeoutSeconds;

    private Instant scheduledAt;

    private Instant startedAt;

    private Instant completedAt;

    private Instant createdAt;

    private String errorMessage;
}

package com.sujith.scheduler.event;

import com.sujith.scheduler.model.JobStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class JobEvent {

    private UUID jobId;
    private String jobName;
    private JobStatus status;
    private Instant timestamp;
    private String errorMessage;
}

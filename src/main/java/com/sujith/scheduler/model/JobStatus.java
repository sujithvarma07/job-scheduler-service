package com.sujith.scheduler.model;

public enum JobStatus {
    PENDING,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    DEAD_LETTER
}

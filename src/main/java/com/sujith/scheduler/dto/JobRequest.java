package com.sujith.scheduler.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class JobRequest {

    @NotBlank
    private String name;

    @NotNull
    private String payload;

    @Min(1)
    @Max(10)
    private int priority = 5;

    private Instant scheduledAt;

    private int maxRetries = 3;

    private int timeoutSeconds = 300;
}

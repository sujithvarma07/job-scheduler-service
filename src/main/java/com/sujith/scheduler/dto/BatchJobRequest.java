package com.sujith.scheduler.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchJobRequest {

    @NotEmpty
    @Size(max = 100)
    @Valid
    private List<JobRequest> jobs;
}

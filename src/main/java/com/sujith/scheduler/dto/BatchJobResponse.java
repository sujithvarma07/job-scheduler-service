package com.sujith.scheduler.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BatchJobResponse {

    private List<JobResponse> submitted;

    private List<String> errors;
}

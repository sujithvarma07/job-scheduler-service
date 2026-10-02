package com.sujith.scheduler.controller;

import com.sujith.scheduler.dto.BatchJobRequest;
import com.sujith.scheduler.dto.BatchJobResponse;
import com.sujith.scheduler.dto.JobRequest;
import com.sujith.scheduler.dto.JobResponse;
import com.sujith.scheduler.model.JobStatus;
import com.sujith.scheduler.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "Submit, inspect, cancel, and requeue scheduled jobs")
public class JobController {

    private final JobService jobService;

    @Operation(summary = "Submit a job", description = "Creates a job and places it on the priority queue.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Job accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    @PostMapping
    public ResponseEntity<JobResponse> submitJob(@Valid @RequestBody JobRequest request) {
        JobResponse response = jobService.submitJob(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Submit a batch of jobs", description = "Submits up to 100 jobs in one call; per-job errors are collected in the response.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Batch processed"),
            @ApiResponse(responseCode = "400", description = "Invalid batch request")
    })
    @PostMapping("/batch")
    public ResponseEntity<BatchJobResponse> submitBatch(@Valid @RequestBody BatchJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.submitBatch(request));
    }

    @Operation(summary = "Get a job by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job found"),
            @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(
            @Parameter(description = "Job id", required = true) @PathVariable UUID id) {
        return ResponseEntity.ok(jobService.getJob(id));
    }

    @Operation(summary = "Cancel a job", description = "Cancels a job that has not started running and is not in a terminal state.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Job cancelled"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
            @ApiResponse(responseCode = "409", description = "Job cannot be cancelled in its current state")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelJob(
            @Parameter(description = "Job id", required = true) @PathVariable UUID id) {
        jobService.cancelJob(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List jobs", description = "Returns a page of jobs filtered by status, priority range, and creation time range.")
    @ApiResponse(responseCode = "200", description = "Page of jobs")
    @GetMapping
    public ResponseEntity<Page<JobResponse>> listJobs(
            @Parameter(description = "Filter by job status") @RequestParam(required = false) JobStatus status,
            @Parameter(description = "Minimum priority (inclusive)") @RequestParam(required = false) Integer minPriority,
            @Parameter(description = "Maximum priority (inclusive)") @RequestParam(required = false) Integer maxPriority,
            @Parameter(description = "Created at or after (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Created at or before (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(jobService.listJobs(status, minPriority, maxPriority, from, to, pageable));
    }

    @Operation(summary = "List dead letter jobs", description = "Returns jobs that exhausted all retries.")
    @ApiResponse(responseCode = "200", description = "Page of dead letter jobs")
    @GetMapping("/dead-letter")
    public ResponseEntity<Page<JobResponse>> listDeadLetterJobs(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(jobService.listDeadLetterJobs(pageable));
    }

    @Operation(summary = "Requeue a dead letter job", description = "Resets retry state and puts a dead letter job back on the queue.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job requeued"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
            @ApiResponse(responseCode = "409", description = "Job is not in the dead letter state")
    })
    @PostMapping("/{id}/requeue")
    public ResponseEntity<JobResponse> requeueJob(
            @Parameter(description = "Job id", required = true) @PathVariable UUID id) {
        return ResponseEntity.ok(jobService.requeueJob(id));
    }

    @Operation(summary = "Get job counts by status")
    @ApiResponse(responseCode = "200", description = "Map of status to job count")
    @GetMapping("/stats")
    public ResponseEntity<Map<JobStatus, Long>> getJobStats() {
        return ResponseEntity.ok(jobService.getJobStats());
    }
}

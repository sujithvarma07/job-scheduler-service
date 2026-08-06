package com.sujith.scheduler.service;

import com.sujith.scheduler.dto.JobRequest;
import com.sujith.scheduler.dto.JobResponse;
import com.sujith.scheduler.exception.InvalidJobStateException;
import com.sujith.scheduler.exception.JobNotFoundException;
import com.sujith.scheduler.metrics.JobMetrics;
import com.sujith.scheduler.model.Job;
import com.sujith.scheduler.model.JobStatus;
import com.sujith.scheduler.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobQueueService jobQueueService;

    @Mock
    private JobEventProducer jobEventProducer;

    @Mock
    private JobMetrics jobMetrics;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, jobQueueService, jobEventProducer, jobMetrics);
    }

    private Job buildJob(UUID id, JobStatus status) {
        return Job.builder()
                .id(id)
                .name("test-job")
                .payload("{}")
                .status(status)
                .priority(5)
                .maxRetries(3)
                .retryCount(0)
                .timeoutSeconds(300)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void submitJob_savesJobAsQueuedAndEnqueuesIt() {
        JobRequest request = new JobRequest();
        request.setName("test-job");
        request.setPayload("{}");
        request.setPriority(5);
        request.setMaxRetries(3);

        UUID id = UUID.randomUUID();
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(id);
            }
            return job;
        });

        JobResponse response = jobService.submitJob(request);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(response.getName()).isEqualTo("test-job");

        verify(jobRepository, times(2)).save(any(Job.class));
        verify(jobQueueService, times(1)).enqueue(any(Job.class));
        verify(jobEventProducer, times(1)).publishJobCreated(any(Job.class));
        verify(jobMetrics, times(1)).incrementSubmitted();
    }

    @Test
    void submitJob_setsStatusToPendingBeforeQueuing() {
        JobRequest request = new JobRequest();
        request.setName("pending-check");
        request.setPayload("{}");

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        when(jobRepository.save(jobCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        jobService.submitJob(request);

        assertThat(jobCaptor.getAllValues()).hasSize(2);
        assertThat(jobCaptor.getAllValues().get(0).getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(jobCaptor.getAllValues().get(1).getStatus()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void getJob_returnsResponseWhenJobExists() {
        UUID id = UUID.randomUUID();
        Job job = buildJob(id, JobStatus.RUNNING);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        JobResponse response = jobService.getJob(id);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void getJob_throwsJobNotFoundExceptionWhenMissing() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getJob(id))
                .isInstanceOf(JobNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void cancelJob_marksPendingJobAsCancelled() {
        UUID id = UUID.randomUUID();
        Job job = buildJob(id, JobStatus.PENDING);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        jobService.cancelJob(id);

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void cancelJob_throwsWhenJobIsRunning() {
        UUID id = UUID.randomUUID();
        Job job = buildJob(id, JobStatus.RUNNING);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.cancelJob(id))
                .isInstanceOf(InvalidJobStateException.class)
                .hasMessageContaining("currently running");

        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    void cancelJob_throwsWhenJobIsAlreadyTerminal() {
        UUID id = UUID.randomUUID();
        Job job = buildJob(id, JobStatus.COMPLETED);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.cancelJob(id))
                .isInstanceOf(InvalidJobStateException.class)
                .hasMessageContaining("terminal status");

        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    void cancelJob_throwsJobNotFoundExceptionWhenMissing() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.cancelJob(id))
                .isInstanceOf(JobNotFoundException.class);

        verify(jobRepository, never()).save(any(Job.class));
    }
}

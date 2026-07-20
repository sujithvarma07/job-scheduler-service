package com.sujith.scheduler.worker;

import com.sujith.scheduler.model.Job;
import com.sujith.scheduler.model.JobStatus;
import com.sujith.scheduler.repository.JobRepository;
import com.sujith.scheduler.service.JobEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobTimeoutChecker {

    private final JobRepository jobRepository;
    private final JobEventProducer jobEventProducer;

    @Scheduled(fixedDelay = 60000)
    public void checkForTimedOutJobs() {
        List<Job> runningJobs = jobRepository.findRunningJobs();
        Instant now = Instant.now();

        for (Job job : runningJobs) {
            long elapsedSeconds = now.getEpochSecond() - job.getStartedAt().getEpochSecond();
            if (elapsedSeconds > job.getTimeoutSeconds()) {
                job.setStatus(JobStatus.FAILED);
                job.setErrorMessage("job timed out");
                job.setCompletedAt(now);
                jobRepository.save(job);
                jobEventProducer.publishJobFailed(job);
                log.warn("job {} marked as failed by timeout checker after {} seconds", job.getId(), elapsedSeconds);
            }
        }
    }
}

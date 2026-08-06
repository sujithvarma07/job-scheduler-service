package com.sujith.scheduler.service;

import com.sujith.scheduler.model.Job;
import com.sujith.scheduler.model.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobQueueServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private JobQueueService jobQueueService;

    @BeforeEach
    void setUp() {
        jobQueueService = new JobQueueService(redisTemplate);
    }

    private Job buildJob(UUID id, int priority) {
        return Job.builder()
                .id(id)
                .name("test-job")
                .payload("{}")
                .status(JobStatus.PENDING)
                .priority(priority)
                .maxRetries(3)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void enqueue_addsJobIdToSortedSetWithComputedScore() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        UUID id = UUID.randomUUID();
        Job job = buildJob(id, 5);

        jobQueueService.enqueue(job);

        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(zSetOperations).add(eq("job:queue"), eq(id.toString()), scoreCaptor.capture());
        assertThat(scoreCaptor.getValue()).isLessThan(0);
    }

    @Test
    void enqueue_higherPriorityJobGetsLowerScoreThanLowerPriorityJob() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        UUID highPriorityId = UUID.randomUUID();
        UUID lowPriorityId = UUID.randomUUID();

        jobQueueService.enqueue(buildJob(highPriorityId, 9));
        jobQueueService.enqueue(buildJob(lowPriorityId, 1));

        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(zSetOperations, org.mockito.Mockito.times(2))
                .add(eq("job:queue"), anyString(), scoreCaptor.capture());

        double highPriorityScore = scoreCaptor.getAllValues().get(0);
        double lowPriorityScore = scoreCaptor.getAllValues().get(1);

        // lower score is popped first by ZSetOperations.popMin, so higher priority
        // jobs must land on a lower score than lower priority jobs
        assertThat(highPriorityScore).isLessThan(lowPriorityScore);
    }

    @Test
    void enqueueWithDelay_addsDelayOnTopOfPriorityScore() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        UUID id = UUID.randomUUID();
        Job job = buildJob(id, 5);

        jobQueueService.enqueue(job);
        jobQueueService.enqueueWithDelay(job, 60_000);

        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(zSetOperations, org.mockito.Mockito.times(2))
                .add(eq("job:queue"), eq(id.toString()), scoreCaptor.capture());

        double baseScore = scoreCaptor.getAllValues().get(0);
        double delayedScore = scoreCaptor.getAllValues().get(1);

        assertThat(delayedScore).isGreaterThan(baseScore);
    }

    @Test
    void dequeue_returnsJobIdFromLowestScoreEntry() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        UUID id = UUID.randomUUID();
        ZSetOperations.TypedTuple<String> tuple = ZSetOperations.TypedTuple.of(id.toString(), -1000.0);
        when(zSetOperations.popMin("job:queue", 1)).thenReturn(Set.of(tuple));

        Optional<UUID> result = jobQueueService.dequeue();

        assertThat(result).contains(id);
    }

    @Test
    void dequeue_returnsEmptyWhenQueueIsEmpty() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.popMin("job:queue", 1)).thenReturn(Set.of());

        Optional<UUID> result = jobQueueService.dequeue();

        assertThat(result).isEmpty();
    }

    @Test
    void dequeue_returnsEmptyWhenRedisReturnsNull() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.popMin("job:queue", 1)).thenReturn(null);

        Optional<UUID> result = jobQueueService.dequeue();

        assertThat(result).isEmpty();
    }

    @Test
    void queueSize_returnsCardinalityOfSortedSet() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.zCard("job:queue")).thenReturn(3L);

        assertThat(jobQueueService.queueSize()).isEqualTo(3L);
    }

    @Test
    void queueSize_returnsZeroWhenRedisReturnsNull() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.zCard("job:queue")).thenReturn(null);

        assertThat(jobQueueService.queueSize()).isEqualTo(0L);
    }
}

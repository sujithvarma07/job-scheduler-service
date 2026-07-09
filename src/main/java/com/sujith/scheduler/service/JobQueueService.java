package com.sujith.scheduler.service;

import com.sujith.scheduler.model.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobQueueService {

    private static final String QUEUE_KEY = "job:queue";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Adds a job to the priority queue. The score is computed so that higher priority jobs
     * are dequeued first, with older jobs of the same priority dequeued before newer ones.
     *
     * @param job the job to enqueue
     */
    public void enqueue(Job job) {
        double score = computeScore(job.getPriority());
        redisTemplate.opsForZSet().add(QUEUE_KEY, job.getId().toString(), score);
        log.debug("enqueued job {} with priority {} score {}", job.getId(), job.getPriority(), score);
    }

    /**
     * Removes and returns the id of the highest priority job in the queue, if any.
     *
     * @return the job id with the lowest score, or empty if the queue is empty
     */
    public Optional<UUID> dequeue() {
        Set<ZSetOperations.TypedTuple<String>> popped = redisTemplate.opsForZSet().popMin(QUEUE_KEY, 1);
        if (popped == null || popped.isEmpty()) {
            return Optional.empty();
        }
        String value = popped.iterator().next().getValue();
        if (value == null) {
            return Optional.empty();
        }
        UUID jobId = UUID.fromString(value);
        log.debug("dequeued job {}", jobId);
        return Optional.of(jobId);
    }

    /**
     * @return the current number of jobs waiting in the queue
     */
    public long queueSize() {
        Long size = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
        return size == null ? 0L : size;
    }

    private double computeScore(int priority) {
        return -priority * 1e12 + System.currentTimeMillis();
    }
}

package com.sujith.scheduler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private static final String LOCK_VALUE = "locked";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Attempts to acquire a distributed lock for the given key using Redis SETNX semantics.
     *
     * @param key        the lock key
     * @param ttlSeconds how long the lock should live before automatically expiring
     * @return true if the lock was acquired, false if it is already held
     */
    public boolean acquireLock(String key, long ttlSeconds) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, LOCK_VALUE, Duration.ofSeconds(ttlSeconds));
        boolean result = Boolean.TRUE.equals(acquired);
        if (result) {
            log.debug("acquired lock for key={} ttlSeconds={}", key, ttlSeconds);
        } else {
            log.debug("failed to acquire lock for key={}, already held", key);
        }
        return result;
    }

    /**
     * Releases a previously acquired lock.
     *
     * @param key the lock key to release
     */
    public void releaseLock(String key) {
        redisTemplate.delete(key);
        log.debug("released lock for key={}", key);
    }
}

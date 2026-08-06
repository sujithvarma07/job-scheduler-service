package com.sujith.scheduler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributedLockServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private DistributedLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new DistributedLockService(redisTemplate);
    }

    @Test
    void acquireLock_returnsTrueWhenKeyIsAbsent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("job-lock:1"), eq("locked"), any(Duration.class)))
                .thenReturn(true);

        boolean acquired = lockService.acquireLock("job-lock:1", 30);

        assertThat(acquired).isTrue();
        verify(valueOperations).setIfAbsent("job-lock:1", "locked", Duration.ofSeconds(30));
    }

    @Test
    void acquireLock_returnsFalseWhenAlreadyHeld() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("job-lock:1"), eq("locked"), any(Duration.class)))
                .thenReturn(false);

        boolean acquired = lockService.acquireLock("job-lock:1", 30);

        assertThat(acquired).isFalse();
    }

    @Test
    void acquireLock_returnsFalseWhenRedisReturnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("job-lock:1"), eq("locked"), any(Duration.class)))
                .thenReturn(null);

        boolean acquired = lockService.acquireLock("job-lock:1", 30);

        assertThat(acquired).isFalse();
    }

    @Test
    void releaseLock_deletesTheKey() {
        lockService.releaseLock("job-lock:1");

        verify(redisTemplate).delete("job-lock:1");
    }
}

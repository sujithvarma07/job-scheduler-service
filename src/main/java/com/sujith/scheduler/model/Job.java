package com.sujith.scheduler.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Builder.Default
    @Column(nullable = false)
    private int priority = 5;

    @Builder.Default
    @Column(nullable = false)
    private int maxRetries = 3;

    @Builder.Default
    @Column(nullable = false)
    private int retryCount = 0;

    private Instant scheduledAt;

    private Instant startedAt;

    private Instant completedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

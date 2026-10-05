package com.sujith.scheduler.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "scheduler")
public class AppConfig {

    @Valid
    @NotNull
    private Worker worker = new Worker();

    @Valid
    @NotNull
    private Queue queue = new Queue();

    @Data
    public static class Worker {

        @Min(1)
        @Max(200)
        private int poolSize = 10;
    }

    @Data
    public static class Queue {

        @Min(50)
        @Max(60000)
        private long pollIntervalMs = 500;
    }
}

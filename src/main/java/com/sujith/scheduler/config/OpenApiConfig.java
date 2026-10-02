package com.sujith.scheduler.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Job Scheduler Service API",
                version = "v1",
                description = "Distributed job scheduling service with priority queuing, retries, "
                        + "dead letter handling, and Kafka lifecycle events.",
                contact = @Contact(
                        name = "Sujith Kakarlapudi",
                        email = "sujithvarma07@gmail.com",
                        url = "https://github.com/sujithvarma07/job-scheduler-service"
                )
        )
)
public class OpenApiConfig {
}

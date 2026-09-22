package com.sujith.scheduler.integration;

import com.sujith.scheduler.dto.JobRequest;
import com.sujith.scheduler.dto.JobResponse;
import com.sujith.scheduler.model.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jobscheduler")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/jobs";
    }

    @Test
    void submitGetAndCancelJob() {
        JobRequest request = new JobRequest();
        request.setName("send-welcome-email");
        request.setPayload("{\"userId\":\"123\"}");
        request.setPriority(7);

        ResponseEntity<JobResponse> submitResponse = restTemplate.postForEntity(baseUrl(), request, JobResponse.class);
        assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(submitResponse.getBody()).isNotNull();

        var jobId = submitResponse.getBody().getId();
        assertThat(jobId).isNotNull();
        assertThat(submitResponse.getBody().getStatus()).isEqualTo(JobStatus.QUEUED);

        ResponseEntity<JobResponse> getResponse = restTemplate.getForEntity(baseUrl() + "/" + jobId, JobResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getName()).isEqualTo("send-welcome-email");

        restTemplate.delete(baseUrl() + "/" + jobId);

        ResponseEntity<JobResponse> afterCancel = restTemplate.getForEntity(baseUrl() + "/" + jobId, JobResponse.class);
        assertThat(afterCancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterCancel.getBody()).isNotNull();
        assertThat(afterCancel.getBody().getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void gettingUnknownJobReturnsNotFound() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/00000000-0000-0000-0000-000000000000", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

# job-scheduler-service

A distributed job scheduling system built with Java 21 and Spring Boot 3.3. It supports
priority-based queuing, recurring cron jobs, retries with exponential backoff, a dead letter
queue, job dependencies, idempotent submission, distributed locking via Redis, and lifecycle
event streaming through Kafka.

## Motivation

Most applications need background job processing: sending emails, generating reports, syncing
data. Building this on a raw thread pool works until it doesn't. There is no visibility, no
retries, and nothing survives a restart. This project builds a scheduler that handles those
concerns explicitly.

## Features

- Priority queue in Redis (sorted set), higher priority dequeued first, FIFO within a priority
- Distributed locking (`SET NX` with TTL) so a job runs on exactly one worker
- Asynchronous execution on a bounded thread pool, capped by a semaphore
- Retries with exponential backoff, then a dead letter queue with a requeue endpoint
- Per-job timeouts with a background checker for stuck `RUNNING` jobs
- Job dependencies: a job waits until the job it depends on has `COMPLETED`
- Idempotency keys to make submission safe to retry
- Batch submission of up to 100 jobs per request
- Recurring jobs via cron expressions
- Kafka lifecycle events and a persisted audit log
- Prometheus metrics and OpenAPI documentation

## Stack

- **Java 21 + Spring Boot 3.3**: core framework
- **PostgreSQL 16**: persistent job store, schema managed by Flyway
- **Redis 7**: distributed locks and priority queue
- **Apache Kafka**: job lifecycle event streaming
- **Micrometer + Prometheus**: metrics via Spring Boot Actuator
- **SpringDoc OpenAPI**: API docs and Swagger UI
- **Testcontainers**: integration tests against real PostgreSQL and Kafka
- **Docker + Docker Compose**: local development stack

## Architecture

```
                 +-------------------------------+
   HTTP client   |        JobController          |   /api/v1/jobs
  ------------>  |  (validation, error handler)  |
                 +---------------+---------------+
                                 |
                                 v
                 +-------------------------------+         +------------------+
                 |          JobService           | ------> |   PostgreSQL     |
                 | submit / cancel / requeue /   |         |  jobs            |
                 | batch / idempotency / deps    |         |  job_audit_log   |
                 +-------+---------------+-------+         +------------------+
                         |               |                          ^
                 enqueue |               | publish                  |
                         v               v                          |
           +------------------+   +----------------------+          |
           |  Redis           |   |  Kafka topics        |          |
           |  job:queue (ZSET)|   |  job.created         |          |
           |  job:lock:<id>   |   |  job.started         |          |
           +--------+---------+   |  job.completed       |          |
                    ^             |  job.failed          |          |
          dequeue + |             +----------+-----------+          |
          lock      |                        |                      |
           +--------+---------+              v                      |
           |   JobWorker      |   +----------------------+          |
           | (poll, semaphore,|   |  JobEventConsumer    | ---------+
           |  jobExecutor)    |   |  (audit log writer)  |   audit rows
           +--------+---------+   +----------------------+
                    |
                    | status, retries, dead letter
                    v
              PostgreSQL (jobs)

  Background:  JobTimeoutChecker (every 60s)  -> fails timed-out RUNNING jobs
               CronSchedulerService (every 60s) -> re-submits due recurring jobs
```

### Job lifecycle

```
PENDING -> QUEUED -> RUNNING -> COMPLETED
              ^         |
              |  retry  v
              +------ (failure) --> retries exhausted --> DEAD_LETTER --requeue--> QUEUED
                        |
                        +--> timeout --> FAILED

PENDING / QUEUED --cancel--> CANCELLED
```

## Getting started

### Prerequisites

- Java 21
- Maven 3.9+
- Docker and Docker Compose

### Option 1: run everything in Docker

```bash
docker compose up -d --build
```

This starts PostgreSQL, Redis, Zookeeper, Kafka and the application. The API is available at
`http://localhost:8080`.

### Option 2: run the app locally against Dockerized infrastructure

```bash
docker compose up -d postgres redis zookeeper kafka
mvn spring-boot:run
```

Flyway applies all migrations on startup.

### Running tests

```bash
mvn test
```

Unit tests use JUnit 5 and Mockito. Integration tests use Testcontainers and require a running
Docker daemon.

### Building the image

```bash
docker build -t job-scheduler-service .
```

## Configuration

All settings live in `src/main/resources/application.yml` and can be overridden with
environment variables using Spring Boot's relaxed binding.

| Environment variable | Property | Default | Description |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/jobscheduler` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | `postgres` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | `postgres` | Database password |
| `SPRING_DATA_REDIS_HOST` | `spring.data.redis.host` | `localhost` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `spring.data.redis.port` | `6379` | Redis port |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka bootstrap servers |
| `SPRING_KAFKA_CONSUMER_GROUP_ID` | `spring.kafka.consumer.group-id` | `job-scheduler-group` | Consumer group for the audit log consumer |
| `SERVER_PORT` | `server.port` | `8080` | HTTP port |
| `SCHEDULER_WORKER_POOL_SIZE` | `scheduler.worker.pool-size` | `10` | Worker threads and max concurrent jobs |
| `SCHEDULER_QUEUE_POLL_INTERVAL_MS` | `scheduler.queue.poll-interval-ms` | `500` | Delay between queue polls in milliseconds |

## API reference

Base path: `/api/v1/jobs`. Interactive docs are available at
`http://localhost:8080/swagger-ui.html` and the OpenAPI spec at `/v3/api-docs`.

| Method | Endpoint | Description | Success |
|---|---|---|---|
| POST | `/api/v1/jobs` | Submit a job | 201 |
| POST | `/api/v1/jobs/batch` | Submit up to 100 jobs | 201 |
| GET | `/api/v1/jobs/{id}` | Get a job by id | 200 |
| DELETE | `/api/v1/jobs/{id}` | Cancel a job | 204 |
| GET | `/api/v1/jobs` | List and filter jobs (paginated) | 200 |
| GET | `/api/v1/jobs/dead-letter` | List dead letter jobs (paginated) | 200 |
| POST | `/api/v1/jobs/{id}/requeue` | Requeue a dead letter job | 200 |
| GET | `/api/v1/jobs/stats` | Job counts grouped by status | 200 |

### Submit a job

```bash
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{
        "name": "send-welcome-email",
        "payload": "{\"userId\": 42}",
        "priority": 8,
        "maxRetries": 3,
        "timeoutSeconds": 120,
        "idempotencyKey": "welcome-email-42"
      }'
```

Request fields:

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `name` | string | yes | | Must not be blank |
| `payload` | string | yes | | Arbitrary job payload, typically JSON |
| `priority` | int | no | `5` | 1 (lowest) to 10 (highest) |
| `scheduledAt` | ISO-8601 instant | no | | Desired execution time |
| `maxRetries` | int | no | `3` | Attempts before moving to `DEAD_LETTER` |
| `timeoutSeconds` | int | no | `300` | Execution time limit |
| `idempotencyKey` | string | no | | Repeated submissions return the existing job |
| `dependsOnJobId` | UUID | no | | Job waits until this job has `COMPLETED` |

### Submit a batch

```bash
curl -X POST http://localhost:8080/api/v1/jobs/batch \
  -H "Content-Type: application/json" \
  -d '{
        "jobs": [
          { "name": "resize-image-1", "payload": "{\"id\": 1}" },
          { "name": "resize-image-2", "payload": "{\"id\": 2}", "priority": 9 }
        ]
      }'
```

The response contains `submitted` (the created jobs) and `errors` (messages for jobs that
could not be submitted).

### Get a job

```bash
curl http://localhost:8080/api/v1/jobs/3f1c2b9e-6a7d-4c11-9e2f-0b8a5d4e7c10
```

### Cancel a job

```bash
curl -X DELETE http://localhost:8080/api/v1/jobs/3f1c2b9e-6a7d-4c11-9e2f-0b8a5d4e7c10
```

Returns `409 Conflict` if the job is running or already in a terminal state.

### List and filter jobs

```bash
curl "http://localhost:8080/api/v1/jobs?status=QUEUED&minPriority=5&maxPriority=10&from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z&page=0&size=20&sort=createdAt,desc"
```

All filters are optional. Valid statuses: `PENDING`, `QUEUED`, `RUNNING`, `COMPLETED`,
`FAILED`, `CANCELLED`, `DEAD_LETTER`.

### Dead letter jobs

```bash
curl "http://localhost:8080/api/v1/jobs/dead-letter?page=0&size=20"

curl -X POST http://localhost:8080/api/v1/jobs/3f1c2b9e-6a7d-4c11-9e2f-0b8a5d4e7c10/requeue
```

### Stats

```bash
curl http://localhost:8080/api/v1/jobs/stats
```

```json
{ "QUEUED": 4, "RUNNING": 2, "COMPLETED": 120, "DEAD_LETTER": 1 }
```

### Error format

```json
{
  "timestamp": "2026-10-02T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "validation failed",
  "fieldErrors": { "name": "must not be blank" }
}
```

| Status | Cause |
|---|---|
| 400 | Request validation failed |
| 404 | Job not found |
| 409 | Operation not allowed in the job's current state |

## Recurring jobs

Jobs with a `cronExpression` set are picked up by `CronSchedulerService`, which runs every
minute, evaluates the expression with Spring's `CronExpression`, and re-submits the job once it
has completed and its next fire time has passed. The field is stored on the `Job` entity
(migration `V6`) and is not yet exposed on `JobRequest`.

## Kafka events

Every status transition publishes a `JobEvent` (`jobId`, `jobName`, `status`, `timestamp`,
`errorMessage`) to one of these topics:

| Topic | Published when |
|---|---|
| `job.created` | A job is submitted |
| `job.started` | A worker starts executing a job |
| `job.completed` | A job finishes successfully |
| `job.failed` | A job fails, times out, or moves to the dead letter queue |

`JobEventConsumer` listens on all four topics and writes each event to the `job_audit_log`
table.

## Observability

Actuator endpoints exposed: `/actuator/health`, `/actuator/info`, `/actuator/metrics`,
`/actuator/prometheus`.

| Metric | Type | Description |
|---|---|---|
| `jobs.submitted` | counter | Jobs submitted |
| `jobs.completed` | counter | Jobs completed successfully |
| `jobs.failed` | counter | Jobs that failed |
| `jobs.queue.size` | gauge | Current depth of the Redis queue |
| `jobs.concurrent.active` | gauge | Jobs currently executing |
| `jobs.execution.duration` | timer | Job execution time |

## Database migrations

| Version | Description |
|---|---|
| V1 | Create `jobs` table with status and priority index |
| V2 | Create `job_audit_log` table |
| V3 | Add `timeout_seconds` to jobs |
| V4 | Add unique `idempotency_key` to jobs |
| V5 | Add `depends_on_job_id` foreign key to jobs |
| V6 | Add `cron_expression` to jobs |

## Project structure

```
src/main/java/com/sujith/scheduler
├── config        Redis, Kafka, thread pool, OpenAPI
├── consumer      Kafka audit log consumer
├── controller    REST API
├── dto           Request and response objects
├── event         Kafka event payloads
├── exception     Custom exceptions and global handler
├── mapper        Entity <-> DTO mapping
├── metrics       Micrometer metrics
├── model         JPA entities and enums
├── repository    Spring Data repositories and specifications
├── service       Job, queue, lock, event, and cron services
├── util          Retry backoff helpers
└── worker        Queue worker and timeout checker
```

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

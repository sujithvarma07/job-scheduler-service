# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- API documentation with SpringDoc OpenAPI: `@Operation`, `@ApiResponse` and `@Parameter`
  annotations on every controller method, plus `OpenApiConfig` with title, version, description
  and contact info. Swagger UI is served at `/swagger-ui.html`.
- Asynchronous worker execution using `CompletableFuture.supplyAsync` on the `jobExecutor`
  pool, capped by a `Semaphore` sized to `scheduler.worker.pool-size`.
- `jobs.concurrent.active` gauge tracking the number of jobs currently executing.
- Cron expression support for recurring jobs (`cron_expression` column, migration `V6`).
  `CronSchedulerService` re-submits completed recurring jobs once their next fire time has passed.
- Integration tests with Testcontainers covering the submit, get and cancel flow against real
  PostgreSQL and Kafka containers.
- `docker-compose.yml` with PostgreSQL 16, Redis 7, Zookeeper, Kafka and the application.
- Multi-stage `Dockerfile` (Maven build stage, Temurin 21 JRE Alpine runtime) and `.dockerignore`.
- Job dependencies via `dependsOnJobId` (migration `V5`). Jobs wait until their dependency is
  `COMPLETED`; the worker re-enqueues them with a short delay until then.
- Batch submission endpoint `POST /api/v1/jobs/batch` accepting up to 100 jobs and returning
  per-job errors alongside submitted jobs.
- Idempotency key support on job submission (unique `idempotency_key` column, migration `V4`).
  Duplicate submissions return the existing job instead of creating a new one.
- Advanced filtering on `GET /api/v1/jobs` by priority range and creation time range using a
  JPA `Specification`.
- Unit tests for `JobService`, `DistributedLockService` and `JobQueueService`.
- Prometheus metrics: `jobs.submitted`, `jobs.completed`, `jobs.failed` counters,
  `jobs.queue.size` gauge and `jobs.execution.duration` timer.
- Configurable per-job timeout (`timeoutSeconds`, default 300, migration `V3`) and a
  `JobTimeoutChecker` that fails `RUNNING` jobs exceeding their timeout every minute.
- `GET /api/v1/jobs/stats` endpoint returning job counts grouped by status.
- Cancellation safety check in the worker: cancelled jobs are skipped even after being dequeued.
- Kafka consumer that writes every lifecycle event to a `job_audit_log` table (migration `V2`).
- Kafka producer publishing lifecycle events to `job.created`, `job.started`, `job.completed`
  and `job.failed` topics.
- Dead letter handling: jobs that exhaust their retries move to `DEAD_LETTER`. Added
  `GET /api/v1/jobs/dead-letter` and `POST /api/v1/jobs/{id}/requeue`.
- Retry with exponential backoff (`2^retryCount` seconds) via `RetryUtil`.
- Scheduled `JobWorker` that polls the queue, acquires a distributed lock, and executes jobs on
  a dedicated `ThreadPoolTaskExecutor`.
- Redis priority queue backed by a sorted set (`job:queue`); higher priority jobs are dequeued
  first, ties broken by submission time.
- Redis configuration and `DistributedLockService` using `SET NX` with a TTL.
- Global exception handler returning a structured error body for not found (404), invalid
  state (409) and validation (400) errors.
- REST controller under `/api/v1/jobs` for submitting, fetching, cancelling and listing jobs.
- `JobService` with submit, get, cancel and paginated list operations.
- `JobRequest` / `JobResponse` DTOs and `JobMapper`.
- `Job` JPA entity, `JobStatus` enum, `JobRepository`, and Flyway migration `V1` creating the
  `jobs` table.
- Initial Spring Boot 3.3 project scaffold on Java 21 with PostgreSQL, Redis, Kafka, Actuator
  and Flyway dependencies.

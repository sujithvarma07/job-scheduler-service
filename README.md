# job-scheduler-service

A distributed job scheduling system built with Java and Spring Boot. Supports priority-based queuing, cron scheduling, retry with exponential backoff, distributed locking via Redis, and event streaming through Kafka.

## Motivation

Most applications need background job processing — sending emails, generating reports, syncing data. Rolling this on top of a raw thread pool works until it doesn't: no visibility, no retries, no persistence across restarts. This project builds a production-grade scheduler that handles those concerns explicitly.

## Planned stack

- **Java 21 + Spring Boot 3.3** — core framework
- **PostgreSQL** — persistent job store with Flyway migrations
- **Redis** — distributed locking, priority queue (sorted sets)
- **Apache Kafka** — job lifecycle event streaming
- **Spring Boot Actuator + Prometheus** — observability
- **Docker + Docker Compose** — local development environment

## Architecture overview

```
Client → REST API → Job Service → Redis Priority Queue
                                        ↓
                               Worker Thread Pool
                                        ↓
                          PostgreSQL (result/status store)
                                        ↓
                               Kafka (job events)
```

## Status

Work in progress. Initial project scaffold.

## Running locally

```bash
docker-compose up -d   # starts PostgreSQL, Redis, Kafka
./mvnw spring-boot:run
```

## API (planned)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/v1/jobs | Submit a new job |
| GET | /api/v1/jobs/{id} | Get job status |
| DELETE | /api/v1/jobs/{id} | Cancel a job |
| GET | /api/v1/jobs | List/filter jobs |

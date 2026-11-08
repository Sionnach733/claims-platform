# Claims Platform

A small healthcare claims platform that shows a modern full-stack setup:
Java/Spring Boot microservices, FastAPI, AWS-style
infrastructure (Aurora PostgreSQL, SQS), MongoDB, React, GitHub Actions and test automation.

See [REQUIREMENTS.md](REQUIREMENTS.md) for the full scope.

## Repository layout

| Path | What it is | Status |
|---|---|---|
| `claims-service/` | Java 21 + Spring Boot 4 claims API (PostgreSQL, Flyway) | skeleton |
| `fraud-service/` | Python + FastAPI risk scoring (MongoDB) | phase 4 |
| `claims-ui/` | React + TypeScript UI | phase 5 |
| `infra/` | LocalStack init scripts, Terraform for AWS | partial |
| `docs/adr/` | Architecture Decision Records | phase 7 |

## Prerequisites

- Docker
- JDK 21 (`brew install openjdk@21`, then `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`)

## Run locally

```bash
docker compose up -d                 # postgres, mongo, localstack (SQS)
docker compose ps                    # all three should be "healthy"

cd claims-service
./mvnw spring-boot:run               # http://localhost:8080/actuator/health
```

Or start the service with throwaway Testcontainers databases, so you don't need Compose:

```bash
cd claims-service && ./mvnw spring-boot:test-run
```

## Test

```bash
cd claims-service && ./mvnw verify   # needs Docker for Testcontainers
```

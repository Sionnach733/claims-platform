# Claims Platform

A small healthcare claims platform that shows a modern full-stack setup:
Java/Spring Boot microservices, FastAPI, AWS-style
infrastructure (Aurora PostgreSQL, SQS), MongoDB, React, GitHub Actions and test automation.

See [REQUIREMENTS.md](REQUIREMENTS.md) for the full scope.

## Repository layout

| Path | What it is | Status |
|---|---|---|
| `claims-service/` | Java 21 + Spring Boot 4 claims API (PostgreSQL, Flyway) | submit / get / search |
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

## claims-service API (v1)

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/claims` | Submit a claim → `201 Created` + `Location` |
| `GET` | `/api/v1/claims/{claimNumber}` | Claim detail with lines |
| `GET` | `/api/v1/claims?memberId=&status=&from=&to=&page=&size=&sort=` | Paginated search (summary rows, max `size` 100) |
| `GET` | `/api/v1/members/{memberId}` | Seeded member |
| `GET` | `/api/v1/providers`, `/api/v1/providers/{npi}` | Seeded providers |

Every error is an RFC 7807 `application/problem+json` body: `400` validation (with a per-field
`errors` list), `404` not found, `409` concurrent modification, `422` business rule (with a stable `code`).

```bash
curl -i -X POST localhost:8080/api/v1/claims -H 'Content-Type: application/json' -d '{
  "memberId": "AET100000001",
  "providerNpi": "1234567893",
  "serviceDate": "2026-11-01",
  "lines": [{ "procedureCode": "99213", "diagnosisCode": "J45.909", "units": 1, "billedAmount": 125.50 }]
}'

curl 'localhost:8080/api/v1/claims?memberId=AET100000001&status=SUBMITTED'
```

### Seed data (synthetic, no real PHI)

| Member | Notes | Provider NPI | Notes |
|---|---|---|---|
| `AET100000001` | active, open-ended | `1234567893` | in network |
| `AET100000002` | active until 2026-12-31 | `1987654328` | in network |
| `AET100000003` | active, open-ended | `1456789019` | in network |
| `AET100000004` | **coverage lapsed** 2024-12-31 | `1678901236` | **out of network** |
| `AET100000005` | active from 2026-01-01 | `1321098760` | **out of network** |

Postgres runs on host port **5433** (so it doesn't clash with a locally installed Postgres).

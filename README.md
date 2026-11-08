# Claims Platform

A small healthcare claims platform that shows a modern full-stack setup:
Java/Spring Boot microservices, FastAPI, AWS-style
infrastructure (Aurora PostgreSQL, SQS), MongoDB, React, GitHub Actions and test automation.

See [REQUIREMENTS.md](REQUIREMENTS.md) for the full scope.

## Repository layout

| Path | What it is | Status |
|---|---|---|
| `claims-service/` | Java 21 + Spring Boot 4 claims API (PostgreSQL, Flyway, SQS) | submit, search, adjudicate, events |
| `fraud-service/` | Python 3.13 + FastAPI risk scoring (MongoDB) | scoring, history, health |
| `claims-ui/` | React 19 + TypeScript claims workbench (Vite) | list, submit, detail, actions |
| `infra/` | LocalStack init scripts, Terraform for AWS | partial |
| `docs/adr/` | Architecture Decision Records | phase 7 |

## Prerequisites

- Docker
- JDK 21 (`brew install openjdk@21`, then `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`)
- [uv](https://docs.astral.sh/uv/) for the Python service (`brew install uv`)
- Node.js 22+ for the UI

## Run locally

```bash
docker compose up -d                 # postgres, mongo, localstack (SQS)
docker compose ps                    # all three should be "healthy"

(cd fraud-service && uv run fraud-service)   # http://localhost:8000/docs (Swagger UI)
(cd claims-service && ./mvnw spring-boot:run) # http://localhost:8080/actuator/health
(cd claims-ui && npm install && npm run dev)  # http://localhost:5173 (proxies /api to :8080)
```

Or start the service with throwaway Testcontainers databases, so you don't need Compose:

```bash
cd claims-service && ./mvnw spring-boot:test-run
```

## Test

```bash
cd claims-service && ./mvnw verify                 # JUnit + Testcontainers (Postgres, LocalStack) + WireMock
cd fraud-service  && uv run ruff check . && uv run pytest   # pytest + Testcontainers (MongoDB)
cd claims-ui      && npm run lint && npm run typecheck && npm run test:coverage  # Vitest + RTL + MSW
cd claims-ui      && npx playwright install chromium && npm run e2e  # Playwright against the running stack
```

## claims-ui

| Page | What it does |
|---|---|
| `/` | Search claims by member, status and service-date range; filters live in the URL (shareable) |
| `/claims/new` | Multi-line claim form; zod validation mirrors the server rules (incl. NPI Luhn check); server field errors map back onto inputs |
| `/claims/:claimNumber` | Summary, fraud-risk badge, lines, status timeline, and only the actions the current status allows |

TanStack Query owns server state (cache, retries on 5xx only, invalidation after writes). Component tests
run the real routes against an MSW mock API; Playwright runs the full journey against the real services,
including a suspicious claim that the live fraud-service routes to manual review.

## claims-service API (v1)

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/claims` | Submit a claim → `201 Created` + `Location` |
| `GET` | `/api/v1/claims/{claimNumber}` | Claim detail with lines |
| `GET` | `/api/v1/claims?memberId=&status=&from=&to=&page=&size=&sort=` | Paginated search (summary rows, max `size` 100) |
| `POST` | `/api/v1/claims/{claimNumber}/adjudicate` | Run the rules engine → `APPROVED` / `DENIED` / `IN_REVIEW` |
| `PATCH` | `/api/v1/claims/{claimNumber}/status` | Manual transition `{ "status": "PAID", "reason": "..." }` |
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

## Adjudication

```
SUBMITTED ──► IN_REVIEW ──► APPROVED ──► PAID
    │             │
    ├─────────────┴──► DENIED          (DENIED and PAID are terminal; anything else → 409)
    └──────────────────► APPROVED
```

Rules run in order; the first **deny** wins, otherwise any **review** flag routes to `IN_REVIEW`,
otherwise the claim is **approved** and priced (in-network 80%, out-of-network 50%, `HALF_EVEN`):

| Order | Rule | Outcome |
|---|---|---|
| 10 | `MemberEligibilityRule` | deny `COVERAGE_INACTIVE` if not covered on the service date |
| 20 | `KnownProviderRule` | deny `PROVIDER_UNKNOWN` if the NPI isn't registered |
| 30 | `DuplicateClaimRule` | deny `DUPLICATE_CLAIM` if an earlier claim bills the same member/provider/date/CPT |
| 90 | `FraudRiskRule` | review if fraud-service score ≥ 50, **or if screening is unavailable** |
| 100 | `HighValueReviewRule` | review if billed ≥ `app.adjudication.manual-review-threshold` (5000) |

Add a rule by adding a `@Component` that implements `AdjudicationRule`: nothing else changes.

## Fraud screening (fraud-service)

`POST /v1/score` returns an explainable 0–100 score from rule-based signals (billed amount,
units per line, unit price vs benchmark, procedure/diagnosis mismatch, provider and member velocity),
and stores every assessment in MongoDB (`GET /v1/scores/{claimNumber}` for the history).

- **Pseudonymised storage**: member IDs are HMAC-SHA256 hashed with a secret key before storage;
  velocity checks work on the hash. A TTL index expires documents after `FRAUD_RETENTION_DAYS`.
- **claims-service → fraud-service** uses `RestClient` with connect/read timeouts, a **retry**
  (I/O errors and 5xx only, never 4xx) and a **circuit breaker** (Resilience4j, metrics in Actuator).
- **Fail-safe**: any failure becomes `Unavailable`, which routes the claim to manual review.
  Fraud signals never auto-deny, and an outage never auto-approves.
- The fraud call runs **outside** the DB transaction; the decision is applied afterwards with an
  optimistic version check, so a slow dependency never holds a DB connection.

## Events (transactional outbox → SQS)

Every status change writes a `claim_status_history` row in the **same transaction**. That table is
the audit trail *and* the outbox: `OutboxPublisher` polls unpublished rows
(`FOR UPDATE SKIP LOCKED`, safe with many instances) and sends a `ClaimStatusChanged` event to
the `claim-status-events` SQS queue (with a DLQ). Delivery is at-least-once, so consumers
de-duplicate on `eventId`. Events carry the claim number but no member identifiers.

```bash
docker compose exec localstack awslocal sqs receive-message \
  --queue-url http://localhost:4566/000000000000/claim-status-events --max-number-of-messages 10
```

Postgres runs on host port **5433** (so it doesn't clash with a locally installed Postgres).

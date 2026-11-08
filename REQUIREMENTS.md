# Claims Platform: Requirements

## Context
A portfolio project demonstrating full-stack engineering for a high-volume healthcare claims domain, covering:
- **Java microservices, Spring Boot/REST, FastAPI**
- **Cloud, preferably AWS** (cloud-native services)
- **Relational DB (AWS RDS/Aurora)** + exposure to **NoSQL (MongoDB)**
- **CI/CD pipelines in GitHub Actions**
- **Test automation** as part of the SDLC, high-volume enterprise systems
- **Agile** practices, **adopting AI in development and delivery**

Goal: a small but polished claims platform that touches every item above, runs locally with one command, and is easy to walk through in 20–30 minutes.

## Architecture (3 services + UI)
```
React UI ──> claims-service (Java 21 / Spring Boot) ──> PostgreSQL  (stands in for Aurora PostgreSQL)
                    │  REST call
                    └──> fraud-service (Python / FastAPI) ──> MongoDB (claim risk notes / scoring history)
             claims-service publishes ClaimStatusChanged events → SQS (LocalStack locally)
```
- **claims-service** (core, Java): claims, members, providers, adjudication rules, status lifecycle
- **fraud-service** (FastAPI): scores a claim for fraud/anomaly risk and stores results in MongoDB. It shows FastAPI, NoSQL and a polyglot microservice
- **claims-ui** (React + TypeScript + Vite): submit, search and view claims, and trigger adjudication
- **AWS**: LocalStack locally (SQS). Terraform for real AWS deployment (ECS Fargate, Aurora PostgreSQL, DocumentDB/Atlas, ECR) is written but optional to apply

## Domain model (claims-service)
- **Member**: memberId, name, dateOfBirth, planCode, coverageStart/End
- **Provider**: npi (10 digits, Luhn-validated), name, specialty, inNetwork
- **Claim**: claimNumber, memberId, providerNpi, serviceDate, status, totalBilled/Allowed/Paid, denialReason, riskScore, `@Version`
- **ClaimLine**: CPT procedureCode, ICD-10 diagnosisCode, units, billedAmount
- **ClaimStatusHistory**: audit trail (from, to, by, at, reason)

Lifecycle: `SUBMITTED → IN_REVIEW → APPROVED | DENIED → PAID`. Invalid transitions return 409.

## Functional requirements
**claims-service (`/api/v1`)**
1. `POST /claims`: validate and persist (SUBMITTED), return 201 + Location
2. `GET /claims/{claimNumber}`, `GET /claims?memberId=&status=&from=&to=&page=&size=` (paginated)
3. `POST /claims/{claimNumber}/adjudicate`: calls fraud-service, then runs pluggable rules (Strategy pattern, `List<AdjudicationRule>`):
   - coverage inactive on serviceDate → DENY
   - unknown provider → DENY
   - duplicate (member + provider + date + CPT) → DENY
   - riskScore ≥ threshold → IN_REVIEW (manual review)
   - allowed = billed × 80% in-network / 50% out-of-network (`BigDecimal`)
4. `PATCH /claims/{claimNumber}/status`: guarded by the state machine, writes audit history, publishes an SQS event
5. Members/Providers: read endpoints, seeded via Flyway
6. Resilience: timeout + retry/circuit breaker (Resilience4j) on the fraud-service call. If it's unavailable the claim falls back to IN_REVIEW

**fraud-service (FastAPI)**
7. `POST /score`: rule-based score from the claim payload (high billed amount, many units, provider/diagnosis mismatch). Persists the score + reasons to MongoDB
8. `GET /scores/{claimNumber}`: score history. `/health` endpoint. Auto-generated OpenAPI docs

**claims-ui (React + TS)**
9. Claim submission form with client-side validation
10. Claims list with filters/pagination, plus a detail view showing lines, status history and risk score
11. Adjudicate / status-change actions with clear error messages (shows ProblemDetail errors)

## Non-functional requirements
- **API quality**: RFC 7807 `ProblemDetail` errors through `@RestControllerAdvice`; OpenAPI (springdoc) + Swagger UI
- **Validation**: Bean Validation + custom NPI/CPT/ICD-10 validators; Pydantic models in FastAPI
- **Security**: Spring Security with JWT/Basic, roles `SUBMITTER` and `ADJUDICATOR`, CORS for the UI. **No PHI in logs** (masking), which supports the HIPAA talking point
- **Observability**: Actuator health/metrics, JSON logs, correlation-id propagated UI → Java → FastAPI
- **Data**: Flyway migrations, indexes on memberId/status/serviceDate, optimistic locking; Mongo index on claimNumber
- **Config**: 12-factor, env vars, Spring profiles `local`/`test`/`docker`
- **Code quality**: layered architecture, DTO records, constructor injection; Spotless/Checkstyle (Java), ruff (Python), ESLint/Prettier (TS)

## Test automation
- **Java**: JUnit 5 + Mockito unit tests (rules, state machine, validators), `@WebMvcTest` slices, `@SpringBootTest` + **Testcontainers** (Postgres, LocalStack), WireMock for fraud-service. JaCoCo ≥ 80% gate
- **Python**: pytest + httpx TestClient, mongomock or Testcontainers Mongo
- **UI**: Vitest + React Testing Library; one **Playwright** E2E test (submit → adjudicate → view)
- **API**: Postman collection for the happy path + error cases (optional, can run in CI via Postman CLI/Newman)

## CI/CD (GitHub Actions)
- `ci.yml` on PR: a matrix/per-service job runs lint → test → coverage → Docker build; path filters so only changed services build
- `cd.yml` on main: build and push images to ECR (OIDC role, no long-lived keys) → `terraform plan` (apply gated by manual approval environment)
- Dependabot + CodeQL for supply-chain/security scanning

## Local run / DevOps
- Multi-stage Dockerfiles (non-root) for each service
- `docker-compose.yml`: postgres, mongo, localstack, claims-service, fraud-service, claims-ui, with healthchecks
- `infra/terraform/`: ECS Fargate services, Aurora PostgreSQL Serverless v2, ECR, SQS, ALB (reviewed, not necessarily applied)

## AI & Agile evidence
- `CLAUDE.md` / docs describing how AI assisted development (scaffolding, test generation, code review) and the guardrails used
- `docs/adr/`: 3–4 Architecture Decision Records (Spring vs FastAPI split, Postgres vs Mongo, SQS events, rules Strategy)
- `docs/backlog.md`: user stories with acceptance criteria, split into sprints. GitHub Issues/Projects board optional
- README: architecture diagram, one-command run, sample curls, trade-offs, "what I'd do next"

## Repo layout
```
claims-platform/
  claims-service/   (Maven, Spring Boot 3, Java 21)
  fraud-service/    (FastAPI, Python 3.12, uv/poetry)
  claims-ui/        (Vite + React + TS)
  infra/terraform/
  postman/
  docs/adr/, docs/backlog.md
  docker-compose.yml, README.md, CLAUDE.md
  .github/workflows/ci.yml, cd.yml
```

## Implementation phases (each one is a demoable increment)
1. Setup: `brew install --cask temurin@21`, `git init`, monorepo skeleton, compose with Postgres/Mongo/LocalStack
2. claims-service core: schema, entities, submit/get/search, validation, errors, tests
3. Adjudication rules + state machine + audit + SQS event
4. fraud-service (FastAPI + Mongo) + Resilience4j integration + WireMock tests
5. claims-ui pages + Vitest + Playwright E2E
6. Security, correlation ids, logging, OpenAPI polish
7. GitHub Actions CI/CD, Dockerfiles, Terraform, ADRs, README

## Verification
- `docker compose up --build` brings everything up healthy, and the UI is at `localhost:5173`
- Full flow in the UI: submit claim → adjudicate → see risk score, status history, PAID
- `./mvnw verify`, `pytest`, `npm test`, `npx playwright test` all pass locally and in GitHub Actions
- Stop fraud-service → adjudication falls back to IN_REVIEW (resilience demo)
- SUBMITTER calling adjudicate gets 403. Invalid NPI gets a 400 ProblemDetail

## Talking points
Microservice boundaries and the polyglot choice; Aurora vs Mongo use cases; rules engine extensibility; BigDecimal/optimistic locking; resilience patterns; PHI/HIPAA in logs; Testcontainers vs mocks; GitHub Actions OIDC to AWS; scaling to high claim volumes (SQS/Kafka, read replicas, caching, idempotency keys); how AI was used safely in the SDLC.

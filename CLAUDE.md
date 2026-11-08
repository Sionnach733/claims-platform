# CLAUDE.md

Guidance for AI coding assistants (and humans) working in this repository.

## What this is

A healthcare claims platform: `claims-service` (Java 21 / Spring Boot 4, PostgreSQL, SQS),
`fraud-service` (Python 3.13 / FastAPI, MongoDB), `claims-ui` (React 19 / TypeScript / Vite), and
`infra/terraform` (AWS). See README.md for architecture and docs/adr/ for the decisions behind it.

## Commands

| Task | Command |
|---|---|
| Everything, in Docker | `docker compose up --build -d --wait` (UI http://localhost:3000) |
| Infra only (for IDE runs) | `docker compose up -d postgres mongo localstack` |
| claims-service tests + coverage gate | `cd claims-service && ./mvnw verify` (needs Docker, `JAVA_HOME` = JDK 21) |
| fraud-service lint + tests | `cd fraud-service && uv run ruff check . && uv run ruff format --check . && uv run pytest` |
| claims-ui checks | `cd claims-ui && npm run lint && npm run format:check && npm run typecheck && npm run test:coverage` |
| End-to-end | `cd claims-ui && E2E_BASE_URL=http://localhost:3000 npx playwright test` (stack running) |
| Terraform | `cd infra/terraform && terraform fmt -check && terraform validate` |
| Workflows / Dockerfiles | `actionlint`, `hadolint */Dockerfile` |

Run the relevant checks before saying a change is done. A change isn't done until its tests pass.

## Non-negotiables

- **No PHI in logs, events, fixtures or test data.** Log identifiers and outcomes only. Seed and test data is
  synthetic. Don't log request bodies. Events carry claim numbers, not member identifiers.
- **Money is `BigDecimal` (Java) / `Decimal` (Python) / `NUMERIC` (SQL).** Never `double` or `float`.
  Rounding is explicit (`HALF_EVEN`).
- **Flyway migrations are append-only.** Never edit a migration that has run anywhere; add `V{n+1}__...sql`.
  Hibernate only validates the schema.
- **Claim state changes go through the `Claim` aggregate** (`approve`, `deny`, `sendToReview`, `markPaid`), which
  enforces the state machine and writes the audit/outbox row. Don't set `status` any other way.
- **Every error is RFC 7807** via `Problems.create(...)`. Don't return ad-hoc error JSON.
- **Authorisation is enforced in the API** (`@PreAuthorize`). Hiding UI controls isn't security.
- **No remote calls inside a DB transaction.** See `ClaimService.adjudicate` for the pattern.
- **Secrets come from the environment / Secrets Manager.** The defaults in config are for local dev only.

## Conventions

- Java: constructor injection, records for DTOs, entities never leave the service layer (services return DTOs).
- New adjudication rule: a `@Component` implementing `AdjudicationRule` with an `@Order`, plus a unit test.
  Don't change the engine.
- Tests: unit tests for logic; `@WebMvcTest` for HTTP contracts; Testcontainers (real Postgres, LocalStack,
  Mongo) for integration. No H2 or mongomock. Assert behaviour, not incidental values (see the E2E fraud-score assertions).
- TypeScript: strict mode; server state through TanStack Query hooks in `src/api/hooks.ts`; components tested
  through the real routes with MSW (`src/test/`).
- Python: typed, Pydantic models with camelCase aliases, pure scoring functions, repository protocol for storage.
- Keep comments for *why*, not *what*.

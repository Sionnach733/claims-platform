# Product backlog

How this project was planned and delivered: thin vertical slices, each demoable at the end of its sprint,
with acceptance criteria that became automated tests.

**Personas:** *Intake clerk* (role SUBMITTER) submits claims for providers. *Claims adjudicator* (role
ADJUDICATOR) decides claims and records payment. *Downstream team* consumes claim events. *Support / SRE* runs the platform.

## Sprint 1: Walking skeleton

| Story | Acceptance criteria | Status |
|---|---|---|
| As an engineer, I can run the platform's dependencies locally with one command | `docker compose up` gives healthy Postgres, Mongo and SQS (LocalStack) with the event queue and DLQ created | ✅ |
| As an engineer, the claims service builds and its tests run against a real database | `./mvnw verify` passes using Testcontainers Postgres | ✅ |

## Sprint 2: Claim intake

| Story | Acceptance criteria | Status |
|---|---|---|
| As an intake clerk, I can submit a claim with one or more service lines | `POST /claims` → 201 + Location; totals computed from lines; claim number `CLM-YYYY-NNNNNNNN` | ✅ |
| As an intake clerk, I get clear errors for bad data | Invalid NPI (Luhn), CPT, ICD-10, future service date, non-positive amounts → 400 with a per-field list; unknown member → 422 `MEMBER_NOT_FOUND` | ✅ |
| As an adjudicator, I can find claims | Filter by member, status, service-date range; paginated; page size capped at 100 | ✅ |

## Sprint 3: Adjudication and events

| Story | Acceptance criteria | Status |
|---|---|---|
| As an adjudicator, clean claims are decided automatically | Lapsed coverage → DENIED `COVERAGE_INACTIVE`; unknown provider → DENIED `PROVIDER_UNKNOWN`; earlier identical claim → DENIED `DUPLICATE_CLAIM`; ≥ 5,000 billed → IN_REVIEW; otherwise APPROVED at 80% in-network / 50% out-of-network | ✅ |
| As an adjudicator, I can resolve reviews and record payment | IN_REVIEW → APPROVED/DENIED (reason required to deny); APPROVED → PAID; any other move → 409 listing the allowed transitions | ✅ |
| As an auditor, I can see every status change | History shows from/to, who, when and why | ✅ |
| As a downstream team, I receive an event for every status change | `ClaimStatusChanged` on SQS, at least once, never lost when SQS is down, no member identifiers in the payload | ✅ |

## Sprint 4: Fraud screening

| Story | Acceptance criteria | Status |
|---|---|---|
| As an adjudicator, risky claims are flagged before payment | Score 0–100 with reasons; score ≥ 50 → IN_REVIEW with the reasons in the history | ✅ |
| As the business, a fraud-service outage doesn't stop or weaken adjudication | Timeout, retry (5xx/IO only) and circuit breaker; unavailable screening → IN_REVIEW, never auto-approve | ✅ |
| As a privacy officer, the fraud store holds no raw member IDs | Member IDs HMAC-hashed; scores expire after the retention period | ✅ |

## Sprint 5: Claims workbench UI

| Story | Acceptance criteria | Status |
|---|---|---|
| As an intake clerk, I can submit a multi-line claim in the browser | Inline validation matching the API; server field errors shown on the right input; running total | ✅ |
| As an adjudicator, I can search claims and act on one | Shareable filtered URLs; detail page with risk badge, lines, timeline; only valid actions offered | ✅ |
| As any user, the UI is accessible and works on a phone | Labels, roles, focus styles, skip link; responsive layout | ✅ |

## Sprint 6: Security and operability

| Story | Acceptance criteria | Status |
|---|---|---|
| As the business, only adjudicators can decide claims | Submitter → 403 on adjudicate/status (API-level, proven end to end); audit records the real user | ✅ |
| As support, I can trace one request across services | One `X-Correlation-Id` in UI errors, both services' logs and every error body | ✅ |
| As a privacy officer, PHI never reaches the logs | No PHI logged by design, plus masking of member IDs, SSNs and DOBs in log output | ✅ |
| As an API consumer, the API is documented | OpenAPI + Swagger UI with auth and error responses | ✅ |

## Sprint 7: Delivery

| Story | Acceptance criteria | Status |
|---|---|---|
| As an engineer, I can run the whole product with one command | `docker compose up --build --wait` → UI on :3000; Playwright passes against it | ✅ |
| As a team, every PR is verified automatically | Path-filtered CI: lint, types, tests, coverage gates, E2E on the compose stack; CodeQL; Dependabot | ✅ |
| As a team, main deploys safely to AWS | SHA-tagged images → reviewed Terraform plan → approved apply → stabilise and smoke-test; OIDC, no stored keys | ✅ (pipeline written; needs an AWS account to run) |

## Next up (not started)

- **Idempotent intake:** `Idempotency-Key` header on `POST /claims` so client retries can't create duplicate claims.
- **Member-level data scoping:** submitters see only claims for their provider organisation.
- **Real identity provider:** OIDC (Cognito) in the UI, with a backend-for-frontend holding tokens in HttpOnly cookies.
- **Distributed tracing:** OpenTelemetry (W3C `traceparent`) to X-Ray, replacing the hand-rolled correlation header.
- **Contract tests** between claims-service and fraud-service (Pact or a Postman collection run in CI).
- **Load test** adjudication at expected peak volume and tune the pool sizes, breaker thresholds and autoscaling.
- **Business-editable rules:** decision tables for network rates and thresholds per plan.

# ADR 0001: Two services, Java for claims, Python for fraud scoring

- **Status:** Accepted
- **Date:** 2026-11-08

## Context

The platform has two distinct jobs:

1. **Claims processing**: intake, validation, adjudication rules, money, a status lifecycle and an audit trail.
   It's transactional, correctness-critical and long-lived, and it's what the claims organisation owns.
2. **Fraud/anomaly scoring**: given a claim, return a risk score with reasons. It changes on a different
   cadence (models and rules are tuned often), is advisory rather than authoritative, and is the natural home
   for data-science tooling.

The target organisation uses Java/Spring Boot for enterprise services and FastAPI for Python services.

## Decision

- **claims-service**: Java 21 + Spring Boot. It owns the claim aggregate and its PostgreSQL schema.
- **fraud-service**: Python + FastAPI. It owns scoring and its MongoDB store, behind a small REST contract
  (`POST /v1/score`).
- One UI (React) talks only to claims-service. fraud-service is internal.
- No shared database: each service owns its data, and integration goes through APIs and events.

## Consequences

- ✅ Each service uses the ecosystem best suited to it, and can be deployed and scaled on its own.
- ✅ A fraud-service outage doesn't stop claim intake (see ADR 0004 for how adjudication degrades).
- ✅ The scoring implementation (rules today, an ML model later) can change without touching claims.
- ❌ Two toolchains to build, secure and operate. This is mitigated by identical CI shape, Docker images and
  observability conventions (correlation IDs, JSON logs) in both.
- ❌ A network hop inside adjudication. It's bounded by timeouts, retry and a circuit breaker.
- **Not chosen:** one Java monolith (simpler, but couples fraud iteration to claims releases), or splitting
  claims further (intake / adjudication / payment). That's premature at this size; the aggregate and
  domain events make that split possible later.

# ADR 0002: PostgreSQL (Aurora) for claims, MongoDB (DocumentDB) for fraud scores

- **Status:** Accepted
- **Date:** 2026-11-08

## Context

Claims data is highly relational (members, providers, claims, lines, status history) and has strong
integrity needs: money must add up, a claim must reference a real member, and status changes must be atomic
with their audit record.

Fraud scores are an append-only log of assessments. The `reasons` array varies per score, the model version
changes over time, and the main queries are "history for a claim" and "recent activity for a provider or
member" (velocity), with a retention period.

## Decision

- **claims-service → PostgreSQL 16**, run as **Aurora PostgreSQL Serverless v2** in AWS.
  - Flyway owns the schema (versioned, reviewed migrations); Hibernate only validates it (`ddl-auto: validate`).
  - Money is `NUMERIC(12,2)` / `BigDecimal`. CHECK constraints guard statuses and amounts.
  - Optimistic locking (`@Version`) protects against concurrent updates.
- **fraud-service → MongoDB 7**, run as **Amazon DocumentDB** (or MongoDB Atlas) in AWS.
  - Compound indexes for claim history and velocity windows; a TTL index enforces retention.
  - Amounts stored as `Decimal128`, not floats. Member IDs are HMAC-pseudonymised before storage.

## Consequences

- ✅ Each store fits its access pattern: transactions and constraints for claims, flexible documents and TTL for scores.
- ✅ Locally, plain Postgres and Mongo containers mirror the managed services, and Testcontainers runs the
  same engines in tests (no H2 or mongomock surprises).
- ❌ Two database technologies to operate and back up. Both are managed services in AWS, which limits the toil.
- ❌ DocumentDB isn't 100% MongoDB-compatible. The service uses only basic CRUD, `distinct` and TTL indexes,
  which DocumentDB supports. TLS needs the Amazon RDS CA bundle, which the image includes, pinned by checksum.
- **Not chosen:** JSONB in Postgres for scores (would work, but couples the services' data and loses the
  "each service owns its store" boundary); DynamoDB (good fit for TTL/velocity, but the role asks for MongoDB experience).

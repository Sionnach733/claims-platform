# ADR 0003: Publish claim status events with a transactional outbox and SQS

- **Status:** Accepted
- **Date:** 2026-11-08

## Context

Downstream systems (payments, member notifications, analytics) need to know when a claim changes status.
Writing to the database and then calling SQS directly is a *dual write*: if the process crashes, or SQS is
unavailable, between the two, the database and the event stream disagree. Either an event is lost, or one is
published for a change that rolled back.

## Decision

- Every status change appends a row to `claim_status_history` **in the same transaction** as the change.
  That table is both the audit trail and the **outbox** (`published_at IS NULL` = not yet sent).
- `OutboxPublisher` polls unpublished rows with `SELECT … FOR UPDATE SKIP LOCKED`, sends them to SQS, and
  marks them published. A partial-batch failure commits the rows that were sent and retries the rest later.
- Events carry a stable `eventId` and a `schemaVersion`. Delivery is **at-least-once**, so consumers de-duplicate on `eventId`.
- Events carry the claim number but **no member identifiers** (data minimisation). Consumers that need more
  call the API with their own authorisation.
- SQS standard queue with a DLQ (max 5 receives) and a CloudWatch alarm when the DLQ isn't empty.

## Consequences

- ✅ No lost or phantom events, even when SQS is down (events queue up in the table and drain later).
- ✅ Safe with many service instances: `SKIP LOCKED` hands each poller different rows.
- ✅ The audit trail and the events can never disagree, because they are the same rows.
- ❌ Events are delayed by up to one poll interval (2s), which is fine for claim status changes.
- ❌ A standard queue doesn't guarantee order. Events include `fromStatus`/`toStatus` and timestamps, so a
  consumer can detect a stale event. A FIFO queue with `MessageGroupId = claimNumber` is the upgrade path if
  strict per-claim order is needed.
- **Not chosen:** CDC with Debezium/Kafka (more infrastructure than this needs); publishing in an
  `@TransactionalEventListener(AFTER_COMMIT)` (still loses events if SQS is down at that moment).

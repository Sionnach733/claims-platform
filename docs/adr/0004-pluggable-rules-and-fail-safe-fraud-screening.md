# ADR 0004: Pluggable adjudication rules, and fraud screening that fails safe

- **Status:** Accepted
- **Date:** 2026-11-08

## Context

Adjudication rules change constantly (new payer policies, new edits), and auditors need to know why a claim
was decided the way it was. Fraud screening depends on a separate service that can be slow or down.

## Decision

**Rules engine**
- Each check is an `AdjudicationRule` Spring bean with an `@Order`, returning a sealed `RuleResult`:
  `Pass`, `Deny(code, reason)` or `Review(reason)`.
- `AdjudicationEngine`: the first **Deny** wins and stops evaluation; otherwise any **Review** routes to
  manual review (all reasons kept); otherwise the claim is **approved** and priced (`PricingPolicy`,
  `BigDecimal`, `HALF_EVEN`).
- Rule parameters (network rates, thresholds) are validated `@ConfigurationProperties`, so a bad value fails at startup.
- Every decision is written to the status history with its reason and actor (`system:adjudication-engine`).

**Fraud screening**
- `HttpFraudClient`: connect/read timeouts, **retry** (I/O errors and 5xx only, never 4xx), and a
  **circuit breaker** (Resilience4j, with Micrometer metrics). It never throws: failures become `FraudAssessment.Unavailable`.
- `FraudRiskRule`: score ≥ threshold (50) **or unavailable** → manual review. A fraud signal never auto-denies;
  an outage never auto-approves.
- The fraud call runs **outside the DB transaction**. The decision is then applied in a short transaction with an
  optimistic version check, so a slow dependency can't hold a pooled connection or row locks.

## Consequences

- ✅ Adding a rule is adding a class (open/closed); the engine and existing rules don't change.
- ✅ Decisions are explainable: denial codes, review reasons and fraud reasons are all recorded.
- ✅ A fraud-service outage costs microseconds per claim once the breaker opens, and claims land in a work
  queue instead of failing or being paid without screening.
- ❌ During an outage the manual review queue grows. That's the right trade in healthcare, but it needs an
  operational alert (breaker-open metric) and staffing awareness.
- ❌ Rules are code, not business-editable configuration. A rules DSL or decision tables (e.g. DMN) would be the next step if
  business users need to change rules without a release.

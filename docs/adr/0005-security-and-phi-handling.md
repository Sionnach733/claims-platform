# ADR 0005: JWT resource server, role-based access and PHI handling

- **Status:** Accepted
- **Date:** 2026-11-08

## Context

Claims contain protected health information (PHI): member identity, diagnoses and procedures. HIPAA requires
access control, audit, minimum-necessary disclosure, and protection of data in logs and at rest. Different
staff need different permissions: intake staff submit, adjudicators decide.

## Decision

- **Authentication:** claims-service is a stateless OAuth2 **resource server** that validates JWTs (expiry,
  issuer, signature). Locally, a dev-only endpoint issues HS256 tokens for two configured users. In production an
  identity provider (Cognito/Okta/Entra) issues RS256 tokens and the service validates them against its JWKS.
  The same resource-server code serves both.
- **Authorisation:** roles `SUBMITTER` and `ADJUDICATOR`, enforced with `@PreAuthorize` next to each endpoint.
  The UI hides actions a user can't take, but that's usability only; the API enforces the rules (an
  end-to-end test proves a submitter gets 403).
- **Audit:** every status change records the authenticated username (or the system actor) with a timestamp and reason.
- **Service-to-service:** fraud-service requires an API key (constant-time compare). In AWS this is backed by
  Secrets Manager, with mTLS/IAM as the upgrade path.
- **PHI in logs:** code logs identifiers and outcomes, never names, DOBs or diagnoses. A masking layer on
  both services' log output (member IDs, SSNs, DOBs) is defence in depth.
- **PHI at rest and in transit:** Aurora, DocumentDB and SQS encrypted; DocumentDB TLS enforced; HTTPS at the
  load balancer (TLS 1.2+); databases in subnets with no internet route; fraud-service stores member IDs only as HMAC hashes.
- **Errors** never leak internals: RFC 7807 bodies with a correlation ID; stack traces stay server-side.

## Consequences

- ✅ Least privilege by role, a complete audit trail, and PHI kept out of logs and events by design.
- ✅ Swapping the demo login for a real IdP is configuration, not a rewrite.
- ❌ The demo token endpoint and in-memory users are **not** production identity management, and are documented as such.
- ❌ The UI keeps the token in `sessionStorage` (readable by any script on the page). That's acceptable for a demo with
  short-lived tokens and a strict CSP. Production would use OIDC with a backend-for-frontend and HttpOnly cookies.
- **Next:** member-level data scoping (a submitter sees only their organisation's claims), field-level
  encryption for diagnosis codes, and access-log review / anomaly alerts.

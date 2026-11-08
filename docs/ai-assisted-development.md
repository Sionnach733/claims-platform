# How AI was used to build this

Adopting AI as part of software development and delivery was an explicit goal. This
project was built pair-programming with an AI coding assistant (Claude Code). This note is an honest account of the
workflow, the guardrails, and what the safety net caught.

## Workflow

1. **Human-owned requirements.** The assistant drafted [REQUIREMENTS.md](../REQUIREMENTS.md) from the job
   spec; I reviewed, adjusted scope and approved it before any code was written. The assistant proposed; I decided.
2. **Phase by phase.** Seven small, demoable increments (see [backlog.md](backlog.md)), each reviewed and
   committed before the next started. Decisions that changed behaviour, such as lowering the fraud review threshold
   from 70 to 50 after seeing a suspicious claim auto-approved, were made by me after reviewing results.
3. **Tests as the contract.** Every phase ended with the full suite green *and* a live run of the real system
   (curl, logs, screenshots), not just "it compiles".
4. **Checked, not trusted, versions.** Library versions and APIs (Spring Boot 4, Spring Security 7, MSW 3,
   React Router 8, Testcontainers 2) were verified against the registries and the installed jars before use.

## Guardrails

- [CLAUDE.md](../CLAUDE.md) records the project's non-negotiables (PHI, money, migrations, state changes,
  transactions) so every AI-assisted change starts from the same rules.
- Quality gates the assistant can't skip: compiler and type checks, linters (ruff, ESLint, hadolint,
  actionlint, `terraform validate`), coverage thresholds (JaCoCo 80%, Vitest 80%), and CI.
- No PHI ever went into prompts: all data is synthetic.

## What the safety net caught

These were real defects in AI-written code, each caught by a test, a linter or a live check before commit:

| Defect | Caught by |
|---|---|
| Postgres `CHAR(10)` NPI column didn't match the JPA mapping | Hibernate `ddl-auto: validate` on first test run |
| Outbox partial failure: rolled back its own "published" marks, so events would be re-sent forever | Code review during implementation → regression unit test |
| `save()` on a managed entity inserted an all-null audit row via `merge()` | Testcontainers integration tests (500s) |
| JDK HttpClient's HTTP/2 upgrade broke every call to the fraud service | WireMock client tests |
| Misaligned table columns in the UI (CSS) | Screenshot review; all functional tests were green |
| Spring Security 7 `FACTOR_PASSWORD` authority leaked into JWT roles | Security integration test |
| Token `expiresIn` off by one second (clock read twice) | Security integration test |
| UI container healthcheck failed: Alpine resolves `localhost` to IPv6 | Compose `--wait` health gating |
| DocumentDB CA bundle unreadable by the non-root app user (mode 600) | Inspecting the built image |
| Brittle E2E assertions on exact fraud scores (data accumulates) | Repeated E2E runs against the containers |

**Takeaway:** AI made the work much faster, and the verification is what made it safe. The same gates protect
human-written code too.

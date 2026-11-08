# ADR 0006: Deliver with GitHub Actions to ECS Fargate, infrastructure in Terraform

- **Status:** Accepted
- **Date:** 2026-11-08

## Context

Three services and their data stores need repeatable environments, fast feedback on every change, and a
controlled path to production with no long-lived cloud credentials.

## Decision

- **Containers:** multi-stage Dockerfiles for each service: non-root numeric UID, health checks, layered Spring
  Boot jar, locked Python dependencies (`uv.lock`), and static UI on unprivileged nginx with a strict CSP.
- **Local:** `docker compose up` runs the whole system. Postgres, Mongo and LocalStack (SQS) stand in for the managed services.
- **CI (GitHub Actions):** path-filtered jobs per service (lint, type check, unit and integration tests with
  Testcontainers, coverage gates), then an end-to-end job that boots the compose stack and runs Playwright.
  CodeQL for all languages; Dependabot for every ecosystem.
- **CD:** runs only after CI passes on `main`. It builds and pushes immutable SHA-tagged images (with SBOM and provenance)
  to ECR, runs `terraform plan`, and applies **that saved plan** after manual approval on the
  `production` environment, then waits for ECS to stabilise and smoke-tests the URL.
- **AWS auth:** GitHub OIDC → IAM role trusted only for this repo's `main` branch and `production` environment.
- **Runtime:** ECS Fargate in private subnets behind an ALB (`/api/*` → claims-service, everything else → UI);
  Service Connect for internal names; deployment circuit breaker with automatic rollback; CPU autoscaling for
  claims-service; Aurora Serverless v2; DocumentDB; SQS with DLQ; secrets from Secrets Manager; CloudWatch alarms.

## Consequences

- ✅ Every change is tested the same way locally and in CI. Production applies exactly the plan a human reviewed.
- ✅ No AWS keys in GitHub; a fork or feature branch can't deploy.
- ✅ Fargate removes server patching; immutable tags make rollback a redeploy of a previous SHA.
- ❌ Terraform needs broad permissions. Mitigated with a permissions boundary and reviewed plans (documented in `github-oidc.tf`).
- ❌ One NAT gateway for cost. Use one per AZ for full zone independence.
- **Not chosen:** EKS (more power, more to operate than three services need), App Runner (too little control
  over networking), CloudFormation/CDK (Terraform is more portable and widely used).

# syntax=docker/dockerfile:1.7
# fraud-service: dependencies resolved from uv.lock (reproducible), installed into a venv in a build stage,
# then only the venv is copied into a slim runtime image that runs as a non-root user.

FROM ghcr.io/astral-sh/uv:0.13.7 AS uv

FROM python:3.15-slim-bookworm AS build
COPY --from=uv /uv /bin/uv
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=never
WORKDIR /app
COPY pyproject.toml uv.lock .python-version ./
# Third-party dependencies first: cached until the lockfile changes.
RUN --mount=type=cache,target=/root/.cache/uv uv sync --frozen --no-dev --no-install-project
COPY src/ src/
RUN --mount=type=cache,target=/root/.cache/uv uv sync --frozen --no-dev --no-editable

FROM python:3.15-slim-bookworm
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=10001:10001 /app/.venv /app/.venv
# Amazon DocumentDB requires TLS signed by the Amazon RDS CA, which isn't in Debian's trust store. Pinned by
# checksum: a changed or tampered bundle fails the build instead of being trusted silently.
ADD --chmod=644 --checksum=sha256:90a6f1a7978a1b1b096dca6c57511ca48d3b8cf052f8e704baf358f34ea2f21b \
    https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem /etc/ssl/certs/rds-global-bundle.pem
ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    FRAUD_LOG_FORMAT=json
# Numeric UID so orchestrators (Kubernetes runAsNonRoot, ECS) can verify the container isn't root.
USER 10001:10001
EXPOSE 8000
# The slim image has no curl/wget, so the health check uses Python itself.
HEALTHCHECK --interval=10s --timeout=3s --start-period=15s --retries=5 \
  CMD ["python", "-c", "import sys, urllib.request; sys.exit(0 if urllib.request.urlopen('http://localhost:8000/health/ready', timeout=2).status == 200 else 1)"]
CMD ["fraud-service"]

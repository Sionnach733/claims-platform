"""Correlation IDs and logging (text for local dev, JSON for containers), with PHI masking."""

import json
import logging
import re
import uuid
from contextvars import ContextVar
from datetime import UTC, datetime

from starlette.types import ASGIApp, Message, Receive, Scope, Send

HEADER = "X-Correlation-Id"
_VALID = re.compile(r"^[A-Za-z0-9-]{8,64}$")  # same rule as claims-service: bounded and log-safe

correlation_id: ContextVar[str | None] = ContextVar("correlation_id", default=None)


def accept_or_generate(incoming: str | None) -> str:
    return incoming if incoming and _VALID.match(incoming) else str(uuid.uuid4())


class CorrelationIdMiddleware:
    """Pure ASGI middleware: reads/creates the ID, exposes it to logs via a ContextVar, echoes it back."""

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        headers = {k.decode().lower(): v.decode() for k, v in scope.get("headers", [])}
        cid = accept_or_generate(headers.get(HEADER.lower()))
        token = correlation_id.set(cid)

        async def send_with_header(message: Message) -> None:
            if message["type"] == "http.response.start":
                message.setdefault("headers", []).append((HEADER.encode(), cid.encode()))
            await send(message)

        try:
            await self.app(scope, receive, send_with_header)
        finally:
            correlation_id.reset(token)


_MEMBER_ID = re.compile(r"\bAET\d{6}(\d{3})\b")


def mask_phi(text: str) -> str:
    """Defence in depth, mirroring claims-service's PhiMasker: member IDs keep only their last 3 digits."""
    return _MEMBER_ID.sub(r"AET******\1", text)


class CorrelationIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.correlation_id = correlation_id.get() or "-"
        return True


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        entry = {
            "@timestamp": datetime.fromtimestamp(record.created, UTC).isoformat(),
            "log.level": record.levelname,
            "log.logger": record.name,
            "message": mask_phi(record.getMessage()),
            "service.name": "fraud-service",
            "correlationId": getattr(record, "correlation_id", "-"),
        }
        if record.exc_info:
            entry["error.stack_trace"] = mask_phi(self.formatException(record.exc_info))
        return json.dumps(entry)


class MaskingTextFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        return mask_phi(super().format(record))


def configure_logging(log_format: str = "text") -> None:
    handler = logging.StreamHandler()
    handler.addFilter(CorrelationIdFilter())
    if log_format == "json":
        handler.setFormatter(JsonFormatter())
    else:
        handler.setFormatter(
            MaskingTextFormatter("%(asctime)s %(levelname)s [%(name)s] [%(correlation_id)s] %(message)s")
        )
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(logging.INFO)
    # Route uvicorn's loggers through the same handler/format.
    for name in ("uvicorn", "uvicorn.error", "uvicorn.access"):
        logging.getLogger(name).handlers = []
        logging.getLogger(name).propagate = True

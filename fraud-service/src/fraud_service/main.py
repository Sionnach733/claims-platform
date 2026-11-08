"""Application factory and entrypoint."""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from pymongo import AsyncMongoClient

from fraud_service.api import router, scoring
from fraud_service.config import Settings, get_settings
from fraud_service.observability import CorrelationIdMiddleware, configure_logging
from fraud_service.repository import MongoScoreRepository, ScoreRepository
from fraud_service.service import ScoringService


def create_app(settings: Settings | None = None, repository: ScoreRepository | None = None) -> FastAPI:
    """Pass `repository` to run without MongoDB (tests); otherwise one is built from settings."""
    settings = settings or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        client = None
        repo = repository
        if repo is None:
            client = AsyncMongoClient(
                settings.mongo_uri, serverSelectionTimeoutMS=settings.mongo_timeout_ms, tz_aware=True
            )
            mongo_repo = MongoScoreRepository(client, settings.mongo_database)
            await mongo_repo.ensure_indexes(settings.retention_days)
            repo = mongo_repo
        app.state.repository = repo
        app.state.scoring_service = ScoringService(repo, settings.member_hash_key.get_secret_value().encode())
        yield
        if client is not None:
            await client.close()

    app = FastAPI(
        title="Fraud Service",
        version="0.1.0",
        description="Explainable fraud/anomaly risk scoring for healthcare claims.",
        lifespan=lifespan,
    )
    app.state.settings = settings
    app.add_middleware(CorrelationIdMiddleware)
    app.include_router(router)
    app.include_router(scoring)
    return app


def run() -> None:
    import uvicorn

    settings = get_settings()
    configure_logging(settings.log_format)  # process-wide, so done at the entry point rather than per app
    uvicorn.run(
        "fraud_service.main:create_app",
        factory=True,
        host=settings.host,
        port=settings.port,
        log_config=None,  # keep our handlers (JSON/text + correlation ID)
    )

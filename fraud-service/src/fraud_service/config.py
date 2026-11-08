"""Configuration from environment variables (12-factor). Prefix: FRAUD_."""

from functools import lru_cache
from typing import Literal

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="FRAUD_", env_file=".env", extra="ignore")

    mongo_uri: str = "mongodb://localhost:27017"
    mongo_database: str = "fraud"
    mongo_timeout_ms: int = Field(default=2000, ge=100)

    # Key for pseudonymising member IDs before storage. Must come from a secret store in real
    # environments (e.g. AWS Secrets Manager injected as an env var); this default is for local dev only.
    member_hash_key: SecretStr = SecretStr("local-dev-only-not-a-secret")

    # Shared key that calling services (claims-service) must send as X-API-Key. Dev default only.
    api_key: SecretStr = SecretStr("local-dev-fraud-key")

    # "text" for local development, "json" (one object per line) in containers.
    log_format: Literal["text", "json"] = "text"

    # Score documents are kept for this long, then expired by a MongoDB TTL index (data retention).
    retention_days: int = Field(default=400, ge=1)

    host: str = "0.0.0.0"  # noqa: S104 - must listen on all interfaces inside a container
    port: int = 8000


@lru_cache
def get_settings() -> Settings:
    return Settings()

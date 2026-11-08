"""Persistence for score records. MongoDB in production; an in-memory version backs the API tests."""

from datetime import datetime, timedelta
from typing import Protocol

from bson import Decimal128
from pymongo import ASCENDING, DESCENDING, AsyncMongoClient
from pymongo.asynchronous.collection import AsyncCollection

from fraud_service.models import ScoreRecord
from fraud_service.scoring import Velocity

COLLECTION = "claim_scores"


class ScoreRepository(Protocol):
    async def save(self, record: ScoreRecord) -> None: ...

    async def history(self, claim_number: str) -> list[ScoreRecord]: ...

    async def velocity(self, *, provider_npi: str, member_hash: str, exclude_claim: str, now: datetime) -> Velocity: ...

    async def ping(self) -> bool: ...


class MongoScoreRepository:
    """Score history in a document store: an append-only log of variable-shaped assessments
    (the reasons list differs per score). It is queried by claim and by recent activity windows."""

    def __init__(self, client: AsyncMongoClient, database: str):
        self._client = client
        self._scores: AsyncCollection = client[database][COLLECTION]

    async def ensure_indexes(self, retention_days: int) -> None:
        await self._scores.create_index([("claimNumber", ASCENDING), ("scoredAt", DESCENDING)])
        await self._scores.create_index([("providerNpi", ASCENDING), ("scoredAt", DESCENDING)])
        await self._scores.create_index([("memberHash", ASCENDING), ("scoredAt", DESCENDING)])
        await self._scores.create_index(
            "scoredAt", name="ttl_scoredAt", expireAfterSeconds=int(timedelta(days=retention_days).total_seconds())
        )

    async def save(self, record: ScoreRecord) -> None:
        doc = record.model_dump(by_alias=True, mode="python")
        doc["totalBilled"] = Decimal128(record.total_billed)  # exact decimal, not a float
        doc["serviceDate"] = record.service_date.isoformat()
        doc["reasons"] = [r.model_dump(by_alias=True) for r in record.reasons]
        await self._scores.insert_one(doc)

    async def history(self, claim_number: str) -> list[ScoreRecord]:
        cursor = self._scores.find({"claimNumber": claim_number}, {"_id": 0}).sort("scoredAt", DESCENDING)
        return [self._to_record(doc) async for doc in cursor]

    async def velocity(self, *, provider_npi: str, member_hash: str, exclude_claim: str, now: datetime) -> Velocity:
        others = {"claimNumber": {"$ne": exclude_claim}}
        provider = await self._scores.distinct(
            "claimNumber", {**others, "providerNpi": provider_npi, "scoredAt": {"$gte": now - timedelta(hours=24)}}
        )
        member = await self._scores.distinct(
            "claimNumber", {**others, "memberHash": member_hash, "scoredAt": {"$gte": now - timedelta(days=7)}}
        )
        return Velocity(provider_claims_24h=len(provider), member_claims_7d=len(member))

    async def ping(self) -> bool:
        try:
            await self._client.admin.command("ping")
            return True
        except Exception:  # readiness checks must never raise
            return False

    @staticmethod
    def _to_record(doc: dict) -> ScoreRecord:
        doc["totalBilled"] = doc["totalBilled"].to_decimal()
        return ScoreRecord.model_validate(doc)


class InMemoryScoreRepository:
    """Test double with the same semantics as the Mongo implementation."""

    def __init__(self) -> None:
        self.records: list[ScoreRecord] = []

    async def save(self, record: ScoreRecord) -> None:
        self.records.append(record)

    async def history(self, claim_number: str) -> list[ScoreRecord]:
        matches = [r for r in self.records if r.claim_number == claim_number]
        return sorted(matches, key=lambda r: r.scored_at, reverse=True)

    async def velocity(self, *, provider_npi: str, member_hash: str, exclude_claim: str, now: datetime) -> Velocity:
        others = [r for r in self.records if r.claim_number != exclude_claim]
        provider = {
            r.claim_number
            for r in others
            if r.provider_npi == provider_npi and r.scored_at >= now - timedelta(hours=24)
        }
        member = {
            r.claim_number for r in others if r.member_hash == member_hash and r.scored_at >= now - timedelta(days=7)
        }
        return Velocity(provider_claims_24h=len(provider), member_claims_7d=len(member))

    async def ping(self) -> bool:
        return True

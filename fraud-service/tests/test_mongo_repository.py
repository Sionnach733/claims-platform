"""MongoScoreRepository against a real MongoDB (Testcontainers). Needs Docker."""

from datetime import UTC, datetime, timedelta
from decimal import Decimal

import pytest
from pymongo import AsyncMongoClient
from testcontainers.community.mongodb import MongoDbContainer

from fraud_service.models import Reason, RiskBand, ScoreRecord
from fraud_service.repository import COLLECTION, MongoScoreRepository

pytestmark = [pytest.mark.integration, pytest.mark.anyio]

NOW = datetime(2026, 11, 8, 12, 0, tzinfo=UTC)


@pytest.fixture(scope="module")
def mongo_url():
    with MongoDbContainer("mongo:7.0") as mongo:
        yield mongo.get_connection_url()


@pytest.fixture
async def repo(mongo_url):
    client = AsyncMongoClient(mongo_url, tz_aware=True)
    db = f"fraud_test_{datetime.now().timestamp():.0f}_{id(client)}"
    repository = MongoScoreRepository(client, db)
    await repository.ensure_indexes(retention_days=400)
    yield repository
    await client.drop_database(db)
    await client.close()


def record(claim: str, *, provider: str = "1234567893", member: str = "m1", at: datetime = NOW, score: int = 10):
    return ScoreRecord(
        claim_number=claim,
        provider_npi=provider,
        member_hash=member,
        service_date="2026-11-01",
        total_billed=Decimal("125.50"),
        score=score,
        risk_band=RiskBand.LOW,
        reasons=[Reason(code="X", description="d", weight=score)],
        model_version="rules-test",
        scored_at=at,
    )


async def test_round_trip_preserves_exact_decimal_and_reasons(repo):
    await repo.save(record("CLM-2026-00000001"))

    [stored] = await repo.history("CLM-2026-00000001")

    assert stored.total_billed == Decimal("125.50")
    assert stored.reasons[0].code == "X"
    assert stored.scored_at == NOW


async def test_history_is_newest_first(repo):
    await repo.save(record("CLM-2026-00000001", at=NOW - timedelta(hours=1), score=5))
    await repo.save(record("CLM-2026-00000001", at=NOW, score=50))

    history = await repo.history("CLM-2026-00000001")

    assert [r.score for r in history] == [50, 5]


async def test_velocity_counts_distinct_other_claims_within_windows(repo):
    await repo.save(record("CLM-2026-00000001"))  # the claim being scored
    await repo.save(record("CLM-2026-00000002"))
    await repo.save(record("CLM-2026-00000002"))  # rescored: counted once
    await repo.save(record("CLM-2026-00000003", at=NOW - timedelta(days=2)))  # outside 24h, inside 7d
    await repo.save(record("CLM-2026-00000004", at=NOW - timedelta(days=8)))  # outside both
    await repo.save(record("CLM-2026-00000005", provider="1987654328", member="m2"))  # different provider/member

    v = await repo.velocity(provider_npi="1234567893", member_hash="m1", exclude_claim="CLM-2026-00000001", now=NOW)

    assert v.provider_claims_24h == 1
    assert v.member_claims_7d == 2


async def test_indexes_include_ttl_for_retention(repo):
    indexes = await repo._scores.index_information()

    assert indexes["ttl_scoredAt"]["expireAfterSeconds"] == 400 * 24 * 3600
    assert any(spec["key"][0][0] == "memberHash" for spec in indexes.values())


async def test_ping(repo):
    assert await repo.ping() is True
    assert repo._scores.name == COLLECTION

"""API tests with the in-memory repository: fast, no Docker needed."""

import logging

import pytest
from fastapi.testclient import TestClient

from fraud_service.config import Settings
from fraud_service.main import create_app
from fraud_service.observability import CorrelationIdFilter
from fraud_service.repository import InMemoryScoreRepository

VALID = {
    "claimNumber": "CLM-2026-00000001",
    "memberId": "AET100000001",
    "providerNpi": "1234567893",
    "serviceDate": "2026-11-01",
    "lines": [{"procedureCode": "99213", "diagnosisCode": "J45.909", "units": 1, "billedAmount": 125.50}],
}


@pytest.fixture
def repo() -> InMemoryScoreRepository:
    return InMemoryScoreRepository()


@pytest.fixture
def client(repo):
    app = create_app(Settings(member_hash_key="test-key", api_key="test-api-key"), repository=repo)
    with TestClient(app, headers={"X-API-Key": "test-api-key"}) as c:  # context manager runs the lifespan
        yield c


def test_score_returns_explainable_assessment(client):
    resp = client.post("/v1/score", json=VALID)

    assert resp.status_code == 200
    body = resp.json()
    assert body["claimNumber"] == "CLM-2026-00000001"
    assert body["score"] == 0
    assert body["riskBand"] == "LOW"
    assert body["reasons"] == []
    assert body["modelVersion"].startswith("rules-")


def test_high_risk_claim_lists_reasons(client):
    risky = {
        **VALID,
        "lines": [{"procedureCode": "27447", "diagnosisCode": "J45.909", "units": 12, "billedAmount": 90000}],
    }

    body = client.post("/v1/score", json=risky).json()

    assert body["riskBand"] == "HIGH"
    assert {r["code"] for r in body["reasons"]} >= {"VERY_HIGH_BILLED_AMOUNT", "EXCESSIVE_UNITS"}


def test_stored_record_is_pseudonymised(client, repo):
    client.post("/v1/score", json=VALID)

    stored = repo.records[0]
    assert stored.member_hash != "AET100000001"
    assert len(stored.member_hash) == 64
    assert "AET100000001" not in stored.model_dump_json()


def test_member_velocity_counts_other_claims_only(client):
    for n in range(1, 4):
        client.post("/v1/score", json={**VALID, "claimNumber": f"CLM-2026-0000000{n}"})
    # Re-scoring claim 1 sees claims 2 and 3 only: below the limit of 3.
    rescored = client.post("/v1/score", json=VALID).json()
    assert rescored["score"] == 0

    fourth = client.post("/v1/score", json={**VALID, "claimNumber": "CLM-2026-00000004"}).json()
    assert [r["code"] for r in fourth["reasons"]] == ["MEMBER_VELOCITY"]


def test_history_is_newest_first(client):
    client.post("/v1/score", json=VALID)
    client.post("/v1/score", json=VALID)

    resp = client.get("/v1/scores/CLM-2026-00000001")

    assert resp.status_code == 200
    history = resp.json()
    assert len(history) == 2
    assert history[0]["scoredAt"] >= history[1]["scoredAt"]


def test_history_for_unknown_claim_is_404(client):
    assert client.get("/v1/scores/CLM-2026-99999999").status_code == 404


@pytest.mark.parametrize(
    ("field", "value"),
    [("providerNpi", "123"), ("memberId", "X1"), ("claimNumber", "nope"), ("lines", [])],
)
def test_invalid_requests_are_422(client, field, value):
    resp = client.post("/v1/score", json={**VALID, field: value})

    assert resp.status_code == 422
    assert any(field in err["loc"] for err in resp.json()["detail"])


def test_health_endpoints(client):
    assert client.get("/health/live").json() == {"status": "UP"}
    assert client.get("/health/ready").json() == {"status": "UP"}


def test_openapi_is_published(client):
    paths = client.get("/openapi.json").json()["paths"]
    assert "/v1/score" in paths


@pytest.mark.parametrize("key", [None, "wrong-key"])
def test_scoring_requires_the_api_key(client, key):
    headers = {"X-API-Key": key} if key else {}
    client.headers.pop("X-API-Key")

    resp = client.post("/v1/score", json=VALID, headers=headers)

    assert resp.status_code == 401
    assert resp.headers["WWW-Authenticate"] == "ApiKey"


def test_health_does_not_need_the_api_key(client):
    client.headers.pop("X-API-Key")

    assert client.get("/health/live").status_code == 200


def test_correlation_id_is_echoed(client):
    resp = client.post("/v1/score", json=VALID, headers={"X-Correlation-Id": "trace-from-claims-1"})

    assert resp.headers["X-Correlation-Id"] == "trace-from-claims-1"


@pytest.mark.parametrize("incoming", [None, "bad id\r\nX-Evil: 1", "x"])
def test_missing_or_unsafe_correlation_id_is_replaced(client, incoming):
    headers = {"X-Correlation-Id": incoming} if incoming else {}

    cid = client.get("/health/live", headers=headers).headers["X-Correlation-Id"]

    assert len(cid) == 36 and cid != incoming


def test_log_lines_carry_the_correlation_id(client):
    """The filter runs when the record is handled, inside the request's context, like the real handler."""
    captured: list[logging.LogRecord] = []
    handler = logging.Handler()
    handler.addFilter(CorrelationIdFilter())
    handler.emit = captured.append
    logger = logging.getLogger("fraud_service.service")
    logger.addHandler(handler)
    logger.setLevel(logging.INFO)
    try:
        client.post("/v1/score", json=VALID, headers={"X-Correlation-Id": "trace-for-logs-1"})
    finally:
        logger.removeHandler(handler)

    record = next(r for r in captured if "Scored claim" in r.getMessage())
    assert record.correlation_id == "trace-for-logs-1"

from decimal import Decimal

import pytest

from fraud_service.models import ClaimLine, ScoreRequest


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"


def make_request(
    claim_number: str = "CLM-2026-00000001",
    member_id: str = "AET100000001",
    provider_npi: str = "1234567893",
    lines: list[tuple[str, str, int, str]] | None = None,
) -> ScoreRequest:
    lines = lines or [("99213", "J45.909", 1, "125.50")]
    return ScoreRequest(
        claim_number=claim_number,
        member_id=member_id,
        provider_npi=provider_npi,
        service_date="2026-11-01",
        lines=[
            ClaimLine(procedure_code=p, diagnosis_code=d, units=u, billed_amount=Decimal(a)) for p, d, u, a in lines
        ],
    )

"""API and storage models. JSON uses camelCase to match the Java claims-service."""

from datetime import date, datetime
from decimal import Decimal
from enum import StrEnum
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, frozen=True)


class ClaimLine(CamelModel):
    procedure_code: Annotated[str, Field(pattern=r"^\d{4}[0-9FTU]$")]
    diagnosis_code: Annotated[str, Field(pattern=r"^[A-TV-Z]\d[0-9A-Z](\.[0-9A-Z]{1,4})?$")]
    units: Annotated[int, Field(ge=1, le=999)]
    billed_amount: Annotated[Decimal, Field(gt=0, max_digits=12, decimal_places=2)]


class ScoreRequest(CamelModel):
    claim_number: Annotated[str, Field(pattern=r"^CLM-\d{4}-\d{8}$")]
    member_id: Annotated[str, Field(pattern=r"^AET\d{9}$")]
    provider_npi: Annotated[str, Field(pattern=r"^\d{10}$")]
    service_date: date
    lines: Annotated[list[ClaimLine], Field(min_length=1, max_length=50)]

    @property
    def total_billed(self) -> Decimal:
        return sum((line.billed_amount for line in self.lines), Decimal("0.00"))


class RiskBand(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class Reason(CamelModel):
    code: str
    description: str
    weight: int


class ScoreResponse(CamelModel):
    claim_number: str
    score: Annotated[int, Field(ge=0, le=100)]
    risk_band: RiskBand
    reasons: list[Reason]
    model_version: str
    scored_at: datetime


class ScoreRecord(CamelModel):
    """What is persisted. Holds a keyed hash of the member ID, never the raw identifier."""

    claim_number: str
    provider_npi: str
    member_hash: str
    service_date: date
    total_billed: Decimal
    score: int
    risk_band: RiskBand
    reasons: list[Reason]
    model_version: str
    scored_at: datetime

    def to_response(self) -> ScoreResponse:
        return ScoreResponse(
            claim_number=self.claim_number,
            score=self.score,
            risk_band=self.risk_band,
            reasons=self.reasons,
            model_version=self.model_version,
            scored_at=self.scored_at,
        )

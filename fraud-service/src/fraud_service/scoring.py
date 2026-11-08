"""Explainable, rule-based risk scoring.

Pure functions only: no I/O, so every signal is unit-testable. Each signal adds a weight and a
human-readable reason; the score is capped at 100. A rules model is a deliberate first step:
it is auditable (important in healthcare), and the same interface could later front an ML model.
"""

from dataclasses import dataclass
from decimal import Decimal

from fraud_service.models import Reason, RiskBand, ScoreRequest

MODEL_VERSION = "rules-2026.11"

HIGH_AMOUNT = Decimal("2500.00")
VERY_HIGH_AMOUNT = Decimal("10000.00")
MAX_UNITS_PER_LINE = 10
BENCHMARK_MULTIPLIER = Decimal("3")
PROVIDER_DAILY_LIMIT = 20
MEMBER_WEEKLY_LIMIT = 3

# Typical unit price by CPT code (illustrative figures, not a real fee schedule).
PRICE_BENCHMARKS: dict[str, Decimal] = {
    "99213": Decimal("130"),
    "99214": Decimal("190"),
    "99285": Decimal("600"),
    "71046": Decimal("90"),
    "94640": Decimal("45"),
    "20610": Decimal("250"),
    "27447": Decimal("2500"),
}


@dataclass(frozen=True)
class Velocity:
    """Recent activity, excluding the claim being scored."""

    provider_claims_24h: int = 0
    member_claims_7d: int = 0


@dataclass(frozen=True)
class Assessment:
    score: int
    risk_band: RiskBand
    reasons: list[Reason]
    model_version: str = MODEL_VERSION


def band_for(score: int) -> RiskBand:
    if score >= 70:
        return RiskBand.HIGH
    if score >= 30:
        return RiskBand.MEDIUM
    return RiskBand.LOW


def assess(request: ScoreRequest, velocity: Velocity) -> Assessment:
    reasons = [
        r
        for r in (
            _billed_amount(request),
            _excessive_units(request),
            _price_above_benchmark(request),
            _procedure_diagnosis_mismatch(request),
            _provider_velocity(velocity),
            _member_velocity(velocity),
        )
        if r is not None
    ]
    score = min(100, sum(r.weight for r in reasons))
    return Assessment(score=score, risk_band=band_for(score), reasons=reasons)


def _billed_amount(req: ScoreRequest) -> Reason | None:
    total = req.total_billed
    if total >= VERY_HIGH_AMOUNT:
        return Reason(
            code="VERY_HIGH_BILLED_AMOUNT", description=f"Total billed {total} ≥ {VERY_HIGH_AMOUNT}", weight=35
        )
    if total >= HIGH_AMOUNT:
        return Reason(code="HIGH_BILLED_AMOUNT", description=f"Total billed {total} ≥ {HIGH_AMOUNT}", weight=15)
    return None


def _excessive_units(req: ScoreRequest) -> Reason | None:
    worst = max(req.lines, key=lambda line: line.units)
    if worst.units > MAX_UNITS_PER_LINE:
        return Reason(
            code="EXCESSIVE_UNITS",
            description=f"{worst.units} units of {worst.procedure_code} on one line (> {MAX_UNITS_PER_LINE})",
            weight=20,
        )
    return None


def _price_above_benchmark(req: ScoreRequest) -> Reason | None:
    for line in req.lines:
        benchmark = PRICE_BENCHMARKS.get(line.procedure_code)
        if benchmark is None:
            continue
        unit_price = line.billed_amount / line.units
        if unit_price > benchmark * BENCHMARK_MULTIPLIER:
            return Reason(
                code="PRICE_ABOVE_BENCHMARK",
                description=(
                    f"{line.procedure_code} billed at {unit_price:.2f}/unit, "
                    f"over {BENCHMARK_MULTIPLIER}× the {benchmark} benchmark"
                ),
                weight=25,
            )
    return None


def _procedure_diagnosis_mismatch(req: ScoreRequest) -> Reason | None:
    """Musculoskeletal surgery (CPT 20000-29999) should carry a musculoskeletal (M) or injury (S) diagnosis."""
    for line in req.lines:
        if line.procedure_code.isdigit() and 20000 <= int(line.procedure_code) <= 29999:
            if line.diagnosis_code[0] not in ("M", "S"):
                return Reason(
                    code="PROCEDURE_DIAGNOSIS_MISMATCH",
                    description=f"Musculoskeletal procedure {line.procedure_code} with unrelated diagnosis category",
                    weight=25,
                )
    return None


def _provider_velocity(v: Velocity) -> Reason | None:
    if v.provider_claims_24h >= PROVIDER_DAILY_LIMIT:
        return Reason(
            code="PROVIDER_VELOCITY",
            description=f"Provider billed {v.provider_claims_24h} other claims in 24h",
            weight=15,
        )
    return None


def _member_velocity(v: Velocity) -> Reason | None:
    if v.member_claims_7d >= MEMBER_WEEKLY_LIMIT:
        return Reason(
            code="MEMBER_VELOCITY",
            description=f"Member has {v.member_claims_7d} other claims in 7 days",
            weight=20,
        )
    return None

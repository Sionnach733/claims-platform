import pytest

from fraud_service.models import RiskBand
from fraud_service.scoring import Velocity, assess, band_for
from tests.conftest import make_request


def codes(assessment) -> list[str]:
    return [r.code for r in assessment.reasons]


def test_clean_routine_claim_scores_zero():
    result = assess(make_request(), Velocity())

    assert result.score == 0
    assert result.risk_band is RiskBand.LOW
    assert result.reasons == []


@pytest.mark.parametrize(
    ("amount", "expected_code", "weight"),
    [("2499.99", None, 0), ("2500.00", "HIGH_BILLED_AMOUNT", 15), ("10000.00", "VERY_HIGH_BILLED_AMOUNT", 35)],
)
def test_billed_amount_thresholds(amount, expected_code, weight):
    # 27447 (knee replacement) has a 2500 benchmark, so these amounts don't also trip the price signal.
    result = assess(make_request(lines=[("27447", "M17.11", 4, amount)]), Velocity())

    assert result.score == weight
    assert codes(result) == ([expected_code] if expected_code else [])


def test_excessive_units_on_a_line():
    result = assess(make_request(lines=[("94640", "J45.909", 11, "440.00")]), Velocity())

    assert codes(result) == ["EXCESSIVE_UNITS"]


def test_unit_price_far_above_benchmark():
    # 99213 benchmark 130; 3x = 390; 400/unit is over.
    result = assess(make_request(lines=[("99213", "J45.909", 1, "400.00")]), Velocity())

    assert codes(result) == ["PRICE_ABOVE_BENCHMARK"]


def test_unknown_procedure_has_no_benchmark_signal():
    result = assess(make_request(lines=[("99999", "J45.909", 1, "900.00")]), Velocity())

    assert result.score == 0


@pytest.mark.parametrize(("diagnosis", "flagged"), [("M17.11", False), ("S83.511A", False), ("J45.909", True)])
def test_musculoskeletal_procedure_needs_matching_diagnosis(diagnosis, flagged):
    result = assess(make_request(lines=[("20610", diagnosis, 1, "200.00")]), Velocity())

    assert ("PROCEDURE_DIAGNOSIS_MISMATCH" in codes(result)) is flagged


def test_velocity_signals():
    result = assess(make_request(), Velocity(provider_claims_24h=20, member_claims_7d=3))

    assert codes(result) == ["PROVIDER_VELOCITY", "MEMBER_VELOCITY"]
    assert result.score == 35
    assert result.risk_band is RiskBand.MEDIUM


def test_velocity_just_below_limits_is_not_flagged():
    result = assess(make_request(), Velocity(provider_claims_24h=19, member_claims_7d=2))

    assert result.score == 0


def test_score_is_capped_at_100_and_high_band():
    result = assess(
        make_request(lines=[("27447", "J45.909", 12, "90000.00")]),
        Velocity(provider_claims_24h=50, member_claims_7d=10),
    )

    assert sum(r.weight for r in result.reasons) > 100
    assert result.score == 100
    assert result.risk_band is RiskBand.HIGH


@pytest.mark.parametrize(
    ("score", "band"),
    [(0, RiskBand.LOW), (29, RiskBand.LOW), (30, RiskBand.MEDIUM), (69, RiskBand.MEDIUM), (70, RiskBand.HIGH)],
)
def test_band_boundaries(score, band):
    assert band_for(score) is band

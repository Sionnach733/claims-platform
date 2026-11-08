"""Application service: pseudonymise, gather velocity, score, persist."""

import hashlib
import hmac
import logging
from collections.abc import Callable
from datetime import UTC, datetime

from fraud_service.models import ScoreRecord, ScoreRequest, ScoreResponse
from fraud_service.repository import ScoreRepository
from fraud_service.scoring import assess

log = logging.getLogger(__name__)


class ScoringService:
    def __init__(
        self, repository: ScoreRepository, member_hash_key: bytes, clock: Callable[[], datetime] | None = None
    ):
        self._repository = repository
        self._key = member_hash_key
        self._clock = clock or (lambda: datetime.now(UTC))

    def pseudonymise(self, member_id: str) -> str:
        """Keyed hash (HMAC-SHA256): stable for velocity checks, but not reversible or guessable without the key."""
        return hmac.new(self._key, member_id.encode(), hashlib.sha256).hexdigest()

    async def score(self, request: ScoreRequest) -> ScoreResponse:
        now = self._clock()
        member_hash = self.pseudonymise(request.member_id)
        velocity = await self._repository.velocity(
            provider_npi=request.provider_npi, member_hash=member_hash, exclude_claim=request.claim_number, now=now
        )
        assessment = assess(request, velocity)
        record = ScoreRecord(
            claim_number=request.claim_number,
            provider_npi=request.provider_npi,
            member_hash=member_hash,
            service_date=request.service_date,
            total_billed=request.total_billed,
            score=assessment.score,
            risk_band=assessment.risk_band,
            reasons=assessment.reasons,
            model_version=assessment.model_version,
            scored_at=now,
        )
        await self._repository.save(record)
        # Identifiers and outcome only: no member data in logs.
        log.info(
            "Scored claim claimNumber=%s score=%d band=%s", request.claim_number, assessment.score, assessment.risk_band
        )
        return record.to_response()

    async def history(self, claim_number: str) -> list[ScoreResponse]:
        return [r.to_response() for r in await self._repository.history(claim_number)]

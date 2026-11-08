"""HTTP routes."""

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Path, Request, Response, status

from fraud_service.models import ScoreRequest, ScoreResponse
from fraud_service.service import ScoringService

router = APIRouter()


def get_service(request: Request) -> ScoringService:
    return request.app.state.scoring_service


Service = Annotated[ScoringService, Depends(get_service)]


@router.post("/v1/score", response_model=ScoreResponse, tags=["scoring"], summary="Score a claim for fraud risk")
async def score_claim(body: ScoreRequest, service: Service) -> ScoreResponse:
    return await service.score(body)


@router.get(
    "/v1/scores/{claim_number}",
    response_model=list[ScoreResponse],
    tags=["scoring"],
    summary="Score history for a claim, newest first",
)
async def score_history(
    claim_number: Annotated[str, Path(pattern=r"^CLM-\d{4}-\d{8}$")], service: Service
) -> list[ScoreResponse]:
    history = await service.history(claim_number)
    if not history:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"No scores for claim {claim_number}")
    return history


@router.get("/health/live", tags=["health"])
async def liveness() -> dict[str, str]:
    return {"status": "UP"}


@router.get("/health/ready", tags=["health"])
async def readiness(request: Request, response: Response) -> dict[str, str]:
    if await request.app.state.repository.ping():
        return {"status": "UP"}
    response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
    return {"status": "DOWN"}

"""Service-to-service authentication: a shared API key in the X-API-Key header.

Adequate for a private network between two services. In AWS this would be replaced or backed by
IAM-authenticated calls, mTLS (e.g. a service mesh) or a private API Gateway; the key itself comes from
Secrets Manager, never from code.
"""

import hmac
from typing import Annotated

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import APIKeyHeader

api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False, description="Shared key for calling services")


def require_api_key(request: Request, provided: Annotated[str | None, Depends(api_key_header)]) -> None:
    expected = request.app.state.settings.api_key.get_secret_value()
    # Constant-time comparison: no timing side channel on the key.
    if provided is None or not hmac.compare_digest(provided.encode(), expected.encode()):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid API key",
            headers={"WWW-Authenticate": "ApiKey"},
        )

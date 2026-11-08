package com.example.claims.api.dto;

import com.example.claims.domain.ClaimStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Manual status change by a claims adjudicator. A reason is mandatory when denying. */
public record UpdateStatusRequest(
        @NotNull ClaimStatus status,
        @Size(max = 500) String reason) {
}

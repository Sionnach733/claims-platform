package com.example.claims.api.dto;

import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Search-result row. Deliberately excludes lines so a page of results never triggers N+1 lazy loads. */
public record ClaimSummaryResponse(
        String claimNumber,
        String memberId,
        String providerNpi,
        LocalDate serviceDate,
        Instant submittedAt,
        ClaimStatus status,
        BigDecimal totalBilled,
        BigDecimal totalPaid) {

    public static ClaimSummaryResponse from(Claim c) {
        return new ClaimSummaryResponse(c.getClaimNumber(), c.getMemberId(), c.getProviderNpi(), c.getServiceDate(),
                c.getSubmittedAt(), c.getStatus(), c.getTotalBilled(), c.getTotalPaid());
    }
}

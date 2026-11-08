package com.example.claims.api.dto;

import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimLine;
import com.example.claims.domain.ClaimStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Full claim detail, including lines. */
public record ClaimResponse(
        String claimNumber,
        String memberId,
        String providerNpi,
        LocalDate serviceDate,
        Instant submittedAt,
        ClaimStatus status,
        BigDecimal totalBilled,
        BigDecimal totalAllowed,
        BigDecimal totalPaid,
        String denialReason,
        BigDecimal riskScore,
        List<Line> lines) {

    public record Line(int lineNumber, String procedureCode, String diagnosisCode, int units, BigDecimal billedAmount) {
        static Line from(ClaimLine l) {
            return new Line(l.getLineNumber(), l.getProcedureCode(), l.getDiagnosisCode(), l.getUnits(), l.getBilledAmount());
        }
    }

    public static ClaimResponse from(Claim c) {
        return new ClaimResponse(
                c.getClaimNumber(), c.getMemberId(), c.getProviderNpi(), c.getServiceDate(), c.getSubmittedAt(),
                c.getStatus(), c.getTotalBilled(), c.getTotalAllowed(), c.getTotalPaid(), c.getDenialReason(),
                c.getRiskScore(), c.getLines().stream().map(Line::from).toList());
    }
}

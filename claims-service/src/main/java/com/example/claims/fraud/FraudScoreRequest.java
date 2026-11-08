package com.example.claims.fraud;

import com.example.claims.domain.Claim;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Wire contract for fraud-service POST /v1/score. */
public record FraudScoreRequest(
        String claimNumber,
        String memberId,
        String providerNpi,
        LocalDate serviceDate,
        List<Line> lines) {

    public record Line(String procedureCode, String diagnosisCode, int units, BigDecimal billedAmount) {
    }

    public static FraudScoreRequest from(Claim claim) {
        return new FraudScoreRequest(claim.getClaimNumber(), claim.getMemberId(), claim.getProviderNpi(),
                claim.getServiceDate(),
                claim.getLines().stream()
                        .map(l -> new Line(l.getProcedureCode(), l.getDiagnosisCode(), l.getUnits(), l.getBilledAmount()))
                        .toList());
    }
}

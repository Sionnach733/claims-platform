package com.example.claims.fraud;

import java.util.List;

/** Wire contract for the fraud-service response. Unknown fields are ignored, so the provider can add fields. */
record FraudScoreResponse(String claimNumber, int score, String riskBand, List<Reason> reasons, String modelVersion) {

    record Reason(String code, String description, int weight) {
    }

    FraudAssessment.Scored toAssessment() {
        List<String> reasonText = reasons == null ? List.of()
                : reasons.stream().map(r -> r.code() + ": " + r.description()).toList();
        return new FraudAssessment.Scored(score, riskBand, reasonText, modelVersion);
    }
}

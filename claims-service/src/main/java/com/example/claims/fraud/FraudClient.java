package com.example.claims.fraud;

/** Port for fraud screening. Implementations never throw: failures come back as {@link FraudAssessment.Unavailable}. */
public interface FraudClient {

    FraudAssessment assess(FraudScoreRequest request);
}

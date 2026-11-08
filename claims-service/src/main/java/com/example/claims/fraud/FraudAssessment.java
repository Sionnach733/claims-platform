package com.example.claims.fraud;

import java.util.List;

/**
 * Result of a fraud check. {@link Unavailable} is a normal, expected value, not an exception:
 * callers must decide what a missing score means (here, route the claim to manual review).
 */
public sealed interface FraudAssessment {

    record Scored(int score, String riskBand, List<String> reasons, String modelVersion) implements FraudAssessment {
    }

    record Unavailable(String reason) implements FraudAssessment {
    }
}

package com.example.claims.service.adjudication;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.claims.domain.Claim;
import com.example.claims.fraud.FraudAssessment;
import com.example.claims.fraud.FraudServiceProperties;
import com.example.claims.service.adjudication.rules.FraudRiskRule;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class FraudRiskRuleTest {

    private final FraudRiskRule rule = new FraudRiskRule(new FraudServiceProperties(URI.create("http://x"),
            Duration.ofSeconds(1), Duration.ofSeconds(1), 70,
            new FraudServiceProperties.Retry(1, Duration.ZERO),
            new FraudServiceProperties.CircuitBreaker(50, 10, 5, Duration.ofSeconds(1), 1), "test-api-key"));

    @ParameterizedTest
    @CsvSource({"0, false", "69, false", "70, true", "100, true"})
    void reviewsAtOrAboveThreshold(int score, boolean review) {
        RuleResult result = rule.evaluate(ctx(new FraudAssessment.Scored(score, "X", List.of("A: a"), "v1")));

        assertThat(result instanceof RuleResult.Review).isEqualTo(review);
    }

    @Test
    void reviewReasonExplainsTheScore() {
        RuleResult result = rule.evaluate(ctx(new FraudAssessment.Scored(85, "HIGH",
                List.of("EXCESSIVE_UNITS: 12 units", "MEMBER_VELOCITY: 4 claims"), "v1")));

        assertThat(result).isInstanceOfSatisfying(RuleResult.Review.class, r -> assertThat(r.reason())
                .isEqualTo("Fraud risk score 85 (HIGH) at or above 70: EXCESSIVE_UNITS: 12 units; MEMBER_VELOCITY: 4 claims"));
    }

    @Test
    void unavailableScreeningFailsSafeToReviewNotApproval() {
        RuleResult result = rule.evaluate(ctx(new FraudAssessment.Unavailable("Fraud screening unavailable (timeout)")));

        assertThat(result).isEqualTo(RuleResult.review("Fraud screening unavailable (timeout)"));
    }

    private static AdjudicationContext ctx(FraudAssessment fraud) {
        Claim claim = new Claim("CLM-1", "AET100000001", "1234567893", LocalDate.of(2026, 11, 1), Instant.EPOCH, "t");
        return new AdjudicationContext(claim, Optional.empty(), Optional.empty(), fraud);
    }
}

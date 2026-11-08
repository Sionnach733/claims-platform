package com.example.claims.service.adjudication.rules;

import com.example.claims.fraud.FraudAssessment;
import com.example.claims.fraud.FraudServiceProperties;
import com.example.claims.service.adjudication.AdjudicationContext;
import com.example.claims.service.adjudication.AdjudicationRule;
import com.example.claims.service.adjudication.RuleResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Route risky claims to a human. Fraud signals never auto-deny: a score is evidence, not a verdict.
 * Fail-safe: if screening was unavailable, the claim is NOT auto-approved but goes to manual review.
 */
@Component
@Order(90)
public class FraudRiskRule implements AdjudicationRule {

    private final int reviewThreshold;

    public FraudRiskRule(FraudServiceProperties properties) {
        this.reviewThreshold = properties.reviewThreshold();
    }

    @Override
    public RuleResult evaluate(AdjudicationContext ctx) {
        return switch (ctx.fraud()) {
            case FraudAssessment.Unavailable u -> RuleResult.review(u.reason());
            case FraudAssessment.Scored s when s.score() >= reviewThreshold -> RuleResult.review(
                    "Fraud risk score %d (%s) at or above %d: %s".formatted(
                            s.score(), s.riskBand(), reviewThreshold, String.join("; ", s.reasons())));
            case FraudAssessment.Scored s -> RuleResult.PASS;
        };
    }
}

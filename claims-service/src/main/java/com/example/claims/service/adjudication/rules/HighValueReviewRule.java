package com.example.claims.service.adjudication.rules;

import com.example.claims.service.adjudication.AdjudicationContext;
import com.example.claims.service.adjudication.AdjudicationProperties;
import com.example.claims.service.adjudication.AdjudicationRule;
import com.example.claims.service.adjudication.RuleResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Route high-value claims to a human instead of auto-approving them. */
@Component
@Order(100)
public class HighValueReviewRule implements AdjudicationRule {

    private final AdjudicationProperties properties;

    public HighValueReviewRule(AdjudicationProperties properties) {
        this.properties = properties;
    }

    @Override
    public RuleResult evaluate(AdjudicationContext ctx) {
        var billed = ctx.claim().getTotalBilled();
        var threshold = properties.manualReviewThreshold();
        return billed.compareTo(threshold) >= 0
                ? RuleResult.review("Billed amount %s is at or above the manual review threshold %s"
                        .formatted(billed.toPlainString(), threshold.toPlainString()))
                : RuleResult.PASS;
    }
}

package com.example.claims.service.adjudication;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Runs the ordered rule chain:
 * <ol>
 *   <li>the first {@link RuleResult.Deny} wins and stops evaluation,</li>
 *   <li>otherwise any {@link RuleResult.Review} sends the claim to manual review (all reasons kept),</li>
 *   <li>otherwise the claim is approved and priced.</li>
 * </ol>
 * Pure decision logic: no persistence, no side effects, so it is trivially unit-testable.
 */
@Component
public class AdjudicationEngine {

    private final List<AdjudicationRule> rules;
    private final PricingPolicy pricing;

    /** Spring injects every AdjudicationRule bean, sorted by @Order. */
    public AdjudicationEngine(List<AdjudicationRule> rules, PricingPolicy pricing) {
        this.rules = List.copyOf(rules);
        this.pricing = pricing;
    }

    public AdjudicationDecision adjudicate(AdjudicationContext context) {
        List<String> reviewReasons = new ArrayList<>();
        for (AdjudicationRule rule : rules) {
            switch (rule.evaluate(context)) {
                case RuleResult.Deny deny -> {
                    return new AdjudicationDecision.Denied(deny.code(), deny.reason(), rule.name());
                }
                case RuleResult.Review review -> reviewReasons.add(review.reason());
                case RuleResult.Pass pass -> { /* continue */ }
            }
        }
        if (!reviewReasons.isEmpty()) {
            return new AdjudicationDecision.NeedsReview(List.copyOf(reviewReasons));
        }
        var rate = pricing.rateFor(context.provider());
        return new AdjudicationDecision.Approved(
                pricing.allowedAmount(context.claim(), context.provider()),
                "Auto-approved: all %d rules passed; priced at %s%% of billed".formatted(
                        rules.size(), rate.movePointRight(2).stripTrailingZeros().toPlainString()));
    }

    public List<String> ruleNames() {
        return rules.stream().map(AdjudicationRule::name).toList();
    }
}

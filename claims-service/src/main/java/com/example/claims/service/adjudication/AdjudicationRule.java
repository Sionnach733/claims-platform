package com.example.claims.service.adjudication;

/**
 * Strategy interface for one adjudication check.
 *
 * <p>To add a rule, create a new {@code @Component} implementing this interface and give it an
 * {@code @Order}. Nothing else changes (open/closed principle). Cheap, decisive checks
 * (eligibility) should run before expensive or advisory ones.
 */
public interface AdjudicationRule {

    RuleResult evaluate(AdjudicationContext context);

    default String name() {
        return getClass().getSimpleName();
    }
}

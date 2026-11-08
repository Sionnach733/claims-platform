package com.example.claims.service.adjudication.rules;

import com.example.claims.service.adjudication.AdjudicationContext;
import com.example.claims.service.adjudication.AdjudicationRule;
import com.example.claims.service.adjudication.RuleResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Deny if the billing provider's NPI is not registered with the plan. */
@Component
@Order(20)
public class KnownProviderRule implements AdjudicationRule {

    public static final String CODE = "PROVIDER_UNKNOWN";

    @Override
    public RuleResult evaluate(AdjudicationContext ctx) {
        return ctx.provider().isPresent()
                ? RuleResult.PASS
                : RuleResult.deny(CODE, "Provider NPI %s is not registered".formatted(ctx.claim().getProviderNpi()));
    }
}

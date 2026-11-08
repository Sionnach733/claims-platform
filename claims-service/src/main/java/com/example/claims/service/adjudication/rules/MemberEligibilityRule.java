package com.example.claims.service.adjudication.rules;

import com.example.claims.service.adjudication.AdjudicationContext;
import com.example.claims.service.adjudication.AdjudicationRule;
import com.example.claims.service.adjudication.RuleResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Deny if the member had no active coverage on the date of service. */
@Component
@Order(10)
public class MemberEligibilityRule implements AdjudicationRule {

    public static final String CODE = "COVERAGE_INACTIVE";

    @Override
    public RuleResult evaluate(AdjudicationContext ctx) {
        var serviceDate = ctx.claim().getServiceDate();
        return ctx.member()
                .map(member -> member.isCoveredOn(serviceDate)
                        ? RuleResult.PASS
                        : RuleResult.deny(CODE, "Member coverage was not active on " + serviceDate))
                .orElseGet(() -> RuleResult.deny(CODE, "Member not found"));
    }
}

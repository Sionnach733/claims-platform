package com.example.claims.service.adjudication.rules;

import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimLine;
import com.example.claims.repository.ClaimRepository;
import com.example.claims.service.adjudication.AdjudicationContext;
import com.example.claims.service.adjudication.AdjudicationRule;
import com.example.claims.service.adjudication.RuleResult;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Deny if an earlier, non-denied claim exists for the same member, provider and date of service
 * that bills any of the same procedure codes. Only <em>earlier</em> claims count, so of two
 * identical submissions the first is processed and the second is the duplicate.
 */
@Component
@Order(30)
public class DuplicateClaimRule implements AdjudicationRule {

    public static final String CODE = "DUPLICATE_CLAIM";

    private final ClaimRepository claims;

    public DuplicateClaimRule(ClaimRepository claims) {
        this.claims = claims;
    }

    @Override
    public RuleResult evaluate(AdjudicationContext ctx) {
        Claim claim = ctx.claim();
        Set<String> procedureCodes = claim.getLines().stream()
                .map(ClaimLine::getProcedureCode)
                .collect(Collectors.toSet());

        return claims.findEarlierDuplicate(claim.getId(), claim.getMemberId(), claim.getProviderNpi(),
                        claim.getServiceDate(), procedureCodes)
                .stream().findFirst()
                .map(original -> RuleResult.deny(CODE, "Duplicate of claim " + original))
                .orElse(RuleResult.PASS);
    }
}

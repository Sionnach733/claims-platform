package com.example.claims.service.adjudication;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimLine;
import com.example.claims.domain.Member;
import com.example.claims.domain.Provider;
import com.example.claims.fraud.FraudAssessment;
import com.example.claims.service.adjudication.rules.HighValueReviewRule;
import com.example.claims.service.adjudication.rules.KnownProviderRule;
import com.example.claims.service.adjudication.rules.MemberEligibilityRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdjudicationEngineTest {

    static final AdjudicationProperties PROPS =
            new AdjudicationProperties(new BigDecimal("0.80"), new BigDecimal("0.50"), new BigDecimal("5000.00"));
    static final PricingPolicy PRICING = new PricingPolicy(PROPS);
    static final FraudAssessment LOW_RISK = new FraudAssessment.Scored(5, "LOW", List.of(), "test");

    static final LocalDate SERVICE_DATE = LocalDate.of(2026, 11, 1);
    static final Member ACTIVE = new Member("AET100000001", "A", "B", LocalDate.of(1980, 1, 1), "PPO",
            LocalDate.of(2024, 1, 1), null);
    static final Member LAPSED = new Member("AET100000004", "C", "D", LocalDate.of(1960, 1, 1), "HMO",
            LocalDate.of(2022, 1, 1), LocalDate.of(2024, 12, 31));
    static final Provider IN_NETWORK = new Provider("1234567893", "GP", "Family Medicine", true);
    static final Provider OUT_OF_NETWORK = new Provider("1678901236", "Imaging", "Radiology", false);

    // ---- engine semantics (with stub rules) -----------------------------------------------

    @Test
    void firstDenyWinsAndLaterRulesAreNotEvaluated() {
        List<String> evaluated = new ArrayList<>();
        var engine = new AdjudicationEngine(List.of(
                recording("a", RuleResult.review("look"), evaluated),
                recording("b", RuleResult.deny("X", "nope"), evaluated),
                recording("c", RuleResult.deny("Y", "never reached"), evaluated)), PRICING);

        var decision = engine.adjudicate(ctx("100.00", Optional.of(ACTIVE), Optional.of(IN_NETWORK)));

        assertThat(decision).isEqualTo(new AdjudicationDecision.Denied("X", "nope", "b"));
        assertThat(evaluated).containsExactly("a", "b");
    }

    @Test
    void allReviewReasonsAreCollected() {
        var engine = new AdjudicationEngine(List.of(
                stub("a", RuleResult.review("one")), stub("b", RuleResult.PASS), stub("c", RuleResult.review("two"))),
                PRICING);

        var decision = engine.adjudicate(ctx("100.00", Optional.of(ACTIVE), Optional.of(IN_NETWORK)));

        assertThat(decision).isEqualTo(new AdjudicationDecision.NeedsReview(List.of("one", "two")));
    }

    // ---- real rules -----------------------------------------------------------------------

    @Test
    void cleanInNetworkClaimIsApprovedAt80Percent() {
        var decision = realEngine().adjudicate(ctx("125.50", Optional.of(ACTIVE), Optional.of(IN_NETWORK)));

        assertThat(decision).isInstanceOfSatisfying(AdjudicationDecision.Approved.class, a ->
                assertThat(a.allowedAmount()).isEqualByComparingTo("100.40"));
    }

    @Test
    void outOfNetworkClaimIsApprovedAt50Percent() {
        var decision = realEngine().adjudicate(ctx("125.50", Optional.of(ACTIVE), Optional.of(OUT_OF_NETWORK)));

        assertThat(decision).isInstanceOfSatisfying(AdjudicationDecision.Approved.class, a ->
                assertThat(a.allowedAmount()).isEqualByComparingTo("62.75"));
    }

    @Test
    void lapsedCoverageIsDenied() {
        var decision = realEngine().adjudicate(ctx("100.00", Optional.of(LAPSED), Optional.of(IN_NETWORK)));

        assertThat(decision).isInstanceOfSatisfying(AdjudicationDecision.Denied.class, d ->
                assertThat(d.code()).isEqualTo(MemberEligibilityRule.CODE));
    }

    @Test
    void unknownProviderIsDenied() {
        var decision = realEngine().adjudicate(ctx("100.00", Optional.of(ACTIVE), Optional.empty()));

        assertThat(decision).isInstanceOfSatisfying(AdjudicationDecision.Denied.class, d ->
                assertThat(d.code()).isEqualTo(KnownProviderRule.CODE));
    }

    @Test
    void eligibilityIsCheckedBeforeProvider() {
        var decision = realEngine().adjudicate(ctx("100.00", Optional.of(LAPSED), Optional.empty()));

        assertThat(decision).isInstanceOfSatisfying(AdjudicationDecision.Denied.class, d ->
                assertThat(d.code()).isEqualTo(MemberEligibilityRule.CODE));
    }

    @Test
    void claimAtThresholdGoesToReview() {
        var decision = realEngine().adjudicate(ctx("5000.00", Optional.of(ACTIVE), Optional.of(IN_NETWORK)));

        assertThat(decision).isInstanceOf(AdjudicationDecision.NeedsReview.class);
    }

    @Test
    void claimJustBelowThresholdIsAutoApproved() {
        var decision = realEngine().adjudicate(ctx("4999.99", Optional.of(ACTIVE), Optional.of(IN_NETWORK)));

        assertThat(decision).isInstanceOf(AdjudicationDecision.Approved.class);
    }

    @Test
    void pricingUsesBankersRounding() {
        // 1.25 × 0.50 = 0.625 → HALF_EVEN → 0.62 (HALF_UP would give 0.63)
        Claim claim = claim("1.25");
        assertThat(PRICING.allowedAmount(claim, Optional.of(OUT_OF_NETWORK))).isEqualByComparingTo("0.62");
    }

    // ---- helpers --------------------------------------------------------------------------

    /** Real rules, minus DuplicateClaimRule (needs the DB; covered by the integration test). */
    private static AdjudicationEngine realEngine() {
        return new AdjudicationEngine(List.of(new MemberEligibilityRule(), new KnownProviderRule(),
                new HighValueReviewRule(PROPS)), PRICING);
    }

    private static AdjudicationContext ctx(String billed, Optional<Member> member, Optional<Provider> provider) {
        return new AdjudicationContext(claim(billed), member, provider, LOW_RISK);
    }

    private static Claim claim(String billed) {
        Claim claim = new Claim("CLM-1", "AET100000001", "1234567893", SERVICE_DATE, Instant.EPOCH, "test");
        claim.addLine(new ClaimLine("99213", "J45.909", 1, new BigDecimal(billed)));
        return claim;
    }

    private static AdjudicationRule stub(String name, RuleResult result) {
        return recording(name, result, new ArrayList<>());
    }

    private static AdjudicationRule recording(String name, RuleResult result, List<String> log) {
        return new AdjudicationRule() {
            @Override
            public RuleResult evaluate(AdjudicationContext context) {
                log.add(name);
                return result;
            }

            @Override
            public String name() {
                return name;
            }
        };
    }
}

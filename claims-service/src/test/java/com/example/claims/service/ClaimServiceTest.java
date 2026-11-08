package com.example.claims.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.claims.api.dto.ClaimLineRequest;
import com.example.claims.api.dto.ClaimResponse;
import com.example.claims.api.dto.SubmitClaimRequest;
import com.example.claims.api.dto.UpdateStatusRequest;
import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimLine;
import com.example.claims.domain.ClaimStatus;
import com.example.claims.domain.InvalidStatusTransitionException;
import com.example.claims.domain.Member;
import com.example.claims.domain.Provider;
import com.example.claims.repository.ClaimRepository;
import com.example.claims.repository.MemberRepository;
import com.example.claims.repository.ProviderRepository;
import com.example.claims.service.adjudication.AdjudicationEngine;
import com.example.claims.service.adjudication.AdjudicationProperties;
import com.example.claims.service.adjudication.PricingPolicy;
import com.example.claims.service.adjudication.rules.HighValueReviewRule;
import com.example.claims.service.adjudication.rules.KnownProviderRule;
import com.example.claims.service.adjudication.rules.MemberEligibilityRule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:15:30Z");
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 11, 1);
    private static final Member MEMBER = new Member("AET100000001", "A", "B", LocalDate.of(1980, 1, 1), "PPO",
            LocalDate.of(2024, 1, 1), null);
    private static final Provider PROVIDER = new Provider("1234567893", "GP", "Family Medicine", true);

    @Mock
    private ClaimRepository claims;

    @Mock
    private MemberRepository members;

    @Mock
    private ProviderRepository providers;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private ClaimService service;

    @BeforeEach
    void setUp() {
        var props = new AdjudicationProperties(new BigDecimal("0.80"), new BigDecimal("0.50"), new BigDecimal("5000"));
        var pricing = new PricingPolicy(props);
        var engine = new AdjudicationEngine(List.of(new MemberEligibilityRule(), new KnownProviderRule(),
                new HighValueReviewRule(props)), pricing);
        service = new ClaimService(claims, members, providers, engine, pricing, new CurrentActor(),
                Clock.fixed(NOW, ZoneOffset.UTC), meters);
    }

    // ---- submit / get / search ------------------------------------------------------------

    @Test
    void submitCreatesSubmittedClaimWithComputedTotalAndNumberedLines() {
        when(members.existsByMemberId("AET100000001")).thenReturn(true);
        when(claims.nextClaimSequence()).thenReturn(42L);
        when(claims.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));

        ClaimResponse claim = service.submit(request(
                new ClaimLineRequest("99213", "J45.909", 1, new BigDecimal("125.50")),
                new ClaimLineRequest("71046", "R05.9", 2, new BigDecimal("80.25"))));

        assertThat(claim.claimNumber()).isEqualTo("CLM-2026-00000042");
        assertThat(claim.status()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(claim.submittedAt()).isEqualTo(NOW);
        assertThat(claim.totalBilled()).isEqualByComparingTo("205.75");
        assertThat(claim.lines()).extracting(ClaimResponse.Line::lineNumber).containsExactly(1, 2);
        assertThat(claim.history()).singleElement()
                .extracting(ClaimResponse.StatusChange::toStatus).isEqualTo(ClaimStatus.SUBMITTED);
    }

    @Test
    void submitRejectsUnknownMemberWithoutSaving() {
        when(members.existsByMemberId("AET100000001")).thenReturn(false);

        assertThatThrownBy(() -> service.submit(request(
                new ClaimLineRequest("99213", "J45.909", 1, new BigDecimal("10.00")))))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("code").isEqualTo("MEMBER_NOT_FOUND");

        verify(claims, never()).save(any());
    }

    @Test
    void getThrowsNotFoundForUnknownClaim() {
        when(claims.findByClaimNumber("CLM-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("CLM-X"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("CLM-X");
    }

    @Test
    void searchRejectsInvertedDateRange() {
        assertThatThrownBy(() -> service.search(null, null,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1), Pageable.unpaged()))
                .isInstanceOf(InvalidRequestException.class);
    }

    // ---- adjudicate -----------------------------------------------------------------------

    @Test
    void adjudicateApprovesCleanClaimAndRecordsSystemActor() {
        givenStoredClaim(claim("100.00"));

        ClaimResponse result = service.adjudicate("CLM-1");

        assertThat(result.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(result.totalAllowed()).isEqualByComparingTo("80.00");
        assertThat(result.history()).last().satisfies(h -> {
            assertThat(h.changedBy()).isEqualTo(CurrentActor.SYSTEM_ADJUDICATOR);
            assertThat(h.reason()).startsWith("Auto-approved");
        });
        assertThat(meters.counter("claims.adjudication.decisions", "outcome", "approved").count()).isEqualTo(1.0);
    }

    @Test
    void adjudicateDeniesWithCodePrefixedReason() {
        Claim claim = claim("100.00");
        when(claims.findByClaimNumber("CLM-1")).thenReturn(Optional.of(claim));
        when(members.findByMemberId("AET100000001")).thenReturn(Optional.of(MEMBER));
        when(providers.findByNpi("1234567893")).thenReturn(Optional.empty());

        ClaimResponse result = service.adjudicate("CLM-1");

        assertThat(result.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(result.denialReason()).startsWith("PROVIDER_UNKNOWN:");
    }

    @Test
    void adjudicatingTwiceIsRejected() {
        Claim claim = claim("100.00");
        claim.sendToReview("x", "y", NOW);
        when(claims.findByClaimNumber("CLM-1")).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.adjudicate("CLM-1"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("already been adjudicated");
        verify(claims, never()).save(any());
    }

    // ---- manual status changes ------------------------------------------------------------

    @Test
    void submittedClaimCannotBeManuallyApproved() {
        when(claims.findByClaimNumber("CLM-1")).thenReturn(Optional.of(claim("100.00")));

        assertThatThrownBy(() -> service.changeStatus("CLM-1", new UpdateStatusRequest(ClaimStatus.APPROVED, null)))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("must be adjudicated first");
    }

    @Test
    void reviewedClaimCanBeApprovedAndPricedThenPaid() {
        Claim claim = claim("6000.00");
        claim.sendToReview("high value", "engine", NOW);
        when(claims.findByClaimNumber("CLM-1")).thenReturn(Optional.of(claim));
        when(providers.findByNpi("1234567893")).thenReturn(Optional.of(PROVIDER));

        service.changeStatus("CLM-1", new UpdateStatusRequest(ClaimStatus.APPROVED, "checked records"));
        ClaimResponse paid = service.changeStatus("CLM-1", new UpdateStatusRequest(ClaimStatus.PAID, null));

        assertThat(paid.status()).isEqualTo(ClaimStatus.PAID);
        assertThat(paid.totalAllowed()).isEqualByComparingTo("4800.00");
        assertThat(paid.totalPaid()).isEqualByComparingTo("4800.00");
        assertThat(paid.history()).extracting(ClaimResponse.StatusChange::changedBy).last().isEqualTo("anonymous");
    }

    @Test
    void denyingRequiresAReason() {
        Claim claim = claim("100.00");
        claim.sendToReview("x", "engine", NOW);
        when(claims.findByClaimNumber("CLM-1")).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.changeStatus("CLM-1", new UpdateStatusRequest(ClaimStatus.DENIED, "  ")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("code").isEqualTo("REASON_REQUIRED");
    }

    // ---- helpers --------------------------------------------------------------------------

    private void givenStoredClaim(Claim claim) {
        when(claims.findByClaimNumber("CLM-1")).thenReturn(Optional.of(claim));
        when(members.findByMemberId("AET100000001")).thenReturn(Optional.of(MEMBER));
        when(providers.findByNpi("1234567893")).thenReturn(Optional.of(PROVIDER));
    }

    private static Claim claim(String billed) {
        Claim claim = new Claim("CLM-1", "AET100000001", "1234567893", SERVICE_DATE, NOW, "submitter");
        claim.addLine(new ClaimLine("99213", "J45.909", 1, new BigDecimal(billed)));
        return claim;
    }

    private static SubmitClaimRequest request(ClaimLineRequest... lines) {
        return new SubmitClaimRequest("AET100000001", "1234567893", SERVICE_DATE, List.of(lines));
    }
}

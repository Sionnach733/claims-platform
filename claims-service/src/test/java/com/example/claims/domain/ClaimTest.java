package com.example.claims.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ClaimTest {

    private static final Instant T0 = Instant.parse("2026-11-08T09:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final Instant T2 = T0.plusSeconds(120);

    @Test
    void newClaimStartsSubmittedWithOneHistoryEntry() {
        Claim claim = claim("100.00");

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(claim.getHistory()).singleElement().satisfies(h -> {
            assertThat(h.getFromStatus()).isNull();
            assertThat(h.getToStatus()).isEqualTo(ClaimStatus.SUBMITTED);
            assertThat(h.getChangedBy()).isEqualTo("submitter");
            assertThat(h.getEventId()).isNotNull();
        });
    }

    @Test
    void approveThenPayRecordsAmountsAndFullAuditTrail() {
        Claim claim = claim("100.00");

        claim.approve(new BigDecimal("80.00"), "auto", "engine", T1);
        claim.markPaid("finance", T2);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(claim.getTotalAllowed()).isEqualByComparingTo("80.00");
        assertThat(claim.getTotalPaid()).isEqualByComparingTo("80.00");
        assertThat(claim.getHistory()).extracting(ClaimStatusHistory::getToStatus)
                .containsExactly(ClaimStatus.SUBMITTED, ClaimStatus.APPROVED, ClaimStatus.PAID);
        assertThat(claim.getHistory()).extracting(ClaimStatusHistory::getFromStatus)
                .containsExactly(null, ClaimStatus.SUBMITTED, ClaimStatus.APPROVED);
        assertThat(claim.getHistory()).extracting(ClaimStatusHistory::getEventId).doesNotHaveDuplicates();
    }

    @Test
    void denialZeroesAmountsAndKeepsReason() {
        Claim claim = claim("100.00");

        claim.deny("COVERAGE_INACTIVE: lapsed", "engine", T1);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.DENIED);
        assertThat(claim.getDenialReason()).isEqualTo("COVERAGE_INACTIVE: lapsed");
        assertThat(claim.getTotalPaid()).isEqualByComparingTo("0");
    }

    @Test
    void illegalTransitionIsRejectedAndLeavesStateUntouched() {
        Claim claim = claim("100.00");

        assertThatThrownBy(() -> claim.markPaid("finance", T1))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Cannot move a claim from SUBMITTED to PAID");

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(claim.getHistory()).hasSize(1);
    }

    @Test
    void deniedClaimCannotBeApproved() {
        Claim claim = claim("100.00");
        claim.deny("duplicate", "engine", T1);

        assertThatThrownBy(() -> claim.approve(new BigDecimal("10.00"), "x", "y", T2))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void allowedAmountCannotExceedBilled() {
        Claim claim = claim("100.00");

        assertThatThrownBy(() -> claim.approve(new BigDecimal("100.01"), "x", "y", T1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
    }

    @Test
    void linesCannotBeAddedAfterAdjudication() {
        Claim claim = claim("100.00");
        claim.sendToReview("check", "engine", T1);

        assertThatThrownBy(() -> claim.addLine(new ClaimLine("99213", "J45.909", 1, BigDecimal.ONE)))
                .isInstanceOf(IllegalStateException.class);
    }

    static Claim claim(String billed) {
        Claim claim = new Claim("CLM-2026-00000001", "AET100000001", "1234567893",
                LocalDate.of(2026, 11, 1), T0, "submitter");
        claim.addLine(new ClaimLine("99213", "J45.909", 1, new BigDecimal(billed)));
        return claim;
    }
}

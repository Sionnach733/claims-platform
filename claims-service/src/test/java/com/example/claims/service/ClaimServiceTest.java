package com.example.claims.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.claims.api.dto.ClaimLineRequest;
import com.example.claims.api.dto.SubmitClaimRequest;
import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimStatus;
import com.example.claims.repository.ClaimRepository;
import com.example.claims.repository.MemberRepository;
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

    @Mock
    private ClaimRepository claims;

    @Mock
    private MemberRepository members;

    private ClaimService service;

    @BeforeEach
    void setUp() {
        service = new ClaimService(claims, members, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void submitCreatesSubmittedClaimWithComputedTotalAndNumberedLines() {
        when(members.existsByMemberId("AET100000001")).thenReturn(true);
        when(claims.nextClaimSequence()).thenReturn(42L);
        when(claims.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));

        Claim claim = service.submit(request(
                new ClaimLineRequest("99213", "J45.909", 1, new BigDecimal("125.50")),
                new ClaimLineRequest("71046", "R05.9", 2, new BigDecimal("80.25"))));

        assertThat(claim.getClaimNumber()).isEqualTo("CLM-2026-00000042");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(claim.getSubmittedAt()).isEqualTo(NOW);
        assertThat(claim.getTotalBilled()).isEqualByComparingTo("205.75");
        assertThat(claim.getLines()).extracting("lineNumber").containsExactly(1, 2);
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

    private static SubmitClaimRequest request(ClaimLineRequest... lines) {
        return new SubmitClaimRequest("AET100000001", "1234567893", LocalDate.of(2026, 11, 1), List.of(lines));
    }
}

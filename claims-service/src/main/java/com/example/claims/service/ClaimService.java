package com.example.claims.service;

import com.example.claims.api.dto.ClaimLineRequest;
import com.example.claims.api.dto.SubmitClaimRequest;
import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimLine;
import com.example.claims.domain.ClaimStatus;
import com.example.claims.repository.ClaimRepository;
import com.example.claims.repository.ClaimSpecifications;
import com.example.claims.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private final ClaimRepository claims;
    private final MemberRepository members;
    private final Clock clock;

    public ClaimService(ClaimRepository claims, MemberRepository members, Clock clock) {
        this.claims = claims;
        this.members = members;
        this.clock = clock;
    }

    @Transactional
    public Claim submit(SubmitClaimRequest request) {
        if (!members.existsByMemberId(request.memberId())) {
            throw new BusinessRuleViolationException("MEMBER_NOT_FOUND",
                    "Member '%s' does not exist".formatted(request.memberId()));
        }

        Instant now = clock.instant();
        Claim claim = new Claim(nextClaimNumber(now), request.memberId(), request.providerNpi(),
                request.serviceDate(), now);
        for (ClaimLineRequest line : request.lines()) {
            claim.addLine(new ClaimLine(line.procedureCode(), line.diagnosisCode(), line.units(), line.billedAmount()));
        }

        Claim saved = claims.save(claim);
        // Log identifiers only: no names, DOBs or diagnosis codes (PHI) in logs.
        log.info("Claim submitted claimNumber={} lines={} totalBilled={}",
                saved.getClaimNumber(), saved.getLines().size(), saved.getTotalBilled());
        return saved;
    }

    @Transactional(readOnly = true)
    public Claim get(String claimNumber) {
        return claims.findByClaimNumber(claimNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimNumber));
    }

    @Transactional(readOnly = true)
    public Page<Claim> search(String memberId, ClaimStatus status, LocalDate from, LocalDate to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("'from' must be on or before 'to'");
        }
        return claims.findAll(ClaimSpecifications.search(memberId, status, from, to), pageable);
    }

    /** e.g. CLM-2026-00000042. Backed by a DB sequence, so it is unique across service instances. */
    private String nextClaimNumber(Instant now) {
        int year = now.atZone(ZoneOffset.UTC).getYear();
        return "CLM-%d-%08d".formatted(year, claims.nextClaimSequence());
    }
}

package com.example.claims.service;

import com.example.claims.api.dto.ClaimLineRequest;
import com.example.claims.api.dto.ClaimResponse;
import com.example.claims.api.dto.ClaimSummaryResponse;
import com.example.claims.api.dto.SubmitClaimRequest;
import com.example.claims.api.dto.UpdateStatusRequest;
import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimLine;
import com.example.claims.domain.ClaimStatus;
import com.example.claims.domain.InvalidStatusTransitionException;
import com.example.claims.domain.Provider;
import com.example.claims.repository.ClaimRepository;
import com.example.claims.repository.ClaimSpecifications;
import com.example.claims.repository.MemberRepository;
import com.example.claims.repository.ProviderRepository;
import com.example.claims.service.adjudication.AdjudicationContext;
import com.example.claims.service.adjudication.AdjudicationDecision;
import com.example.claims.service.adjudication.AdjudicationEngine;
import com.example.claims.service.adjudication.PricingPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for claims. Entities never leave this class: every public method maps to
 * DTOs inside its transaction, so lazy associations are safe and the API can't leak JPA state.
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private final ClaimRepository claims;
    private final MemberRepository members;
    private final ProviderRepository providers;
    private final AdjudicationEngine engine;
    private final PricingPolicy pricing;
    private final CurrentActor actor;
    private final Clock clock;
    private final MeterRegistry meters;

    public ClaimService(ClaimRepository claims, MemberRepository members, ProviderRepository providers,
                        AdjudicationEngine engine, PricingPolicy pricing, CurrentActor actor, Clock clock,
                        MeterRegistry meters) {
        this.claims = claims;
        this.members = members;
        this.providers = providers;
        this.engine = engine;
        this.pricing = pricing;
        this.actor = actor;
        this.clock = clock;
        this.meters = meters;
    }

    @Transactional
    public ClaimResponse submit(SubmitClaimRequest request) {
        if (!members.existsByMemberId(request.memberId())) {
            throw new BusinessRuleViolationException("MEMBER_NOT_FOUND",
                    "Member '%s' does not exist".formatted(request.memberId()));
        }

        Instant now = clock.instant();
        Claim claim = new Claim(nextClaimNumber(now), request.memberId(), request.providerNpi(),
                request.serviceDate(), now, actor.name());
        for (ClaimLineRequest line : request.lines()) {
            claim.addLine(new ClaimLine(line.procedureCode(), line.diagnosisCode(), line.units(), line.billedAmount()));
        }

        Claim saved = claims.save(claim);
        // Log identifiers only: no names, DOBs or diagnosis codes (PHI) in logs.
        log.info("Claim submitted claimNumber={} lines={} totalBilled={}",
                saved.getClaimNumber(), saved.getLines().size(), saved.getTotalBilled());
        return ClaimResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ClaimResponse get(String claimNumber) {
        return ClaimResponse.from(load(claimNumber));
    }

    @Transactional(readOnly = true)
    public Page<ClaimSummaryResponse> search(String memberId, ClaimStatus status, LocalDate from, LocalDate to,
                                             Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("'from' must be on or before 'to'");
        }
        return claims.findAll(ClaimSpecifications.search(memberId, status, from, to), pageable)
                .map(ClaimSummaryResponse::from);
    }

    /**
     * Run the rules engine against a SUBMITTED claim. Not idempotent by design: a second call
     * finds the claim already decided and gets a 409, so a retry can never double-process.
     */
    @Transactional
    public ClaimResponse adjudicate(String claimNumber) {
        Claim claim = load(claimNumber);
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new InvalidStatusTransitionException(claim.getStatus(), ClaimStatus.SUBMITTED,
                    "Claim %s has already been adjudicated (status %s)".formatted(claimNumber, claim.getStatus()));
        }

        Optional<Provider> provider = providers.findByNpi(claim.getProviderNpi());
        var context = new AdjudicationContext(claim, members.findByMemberId(claim.getMemberId()), provider);
        AdjudicationDecision decision = engine.adjudicate(context);
        Instant now = clock.instant();
        String by = CurrentActor.SYSTEM_ADJUDICATOR;

        String outcome = switch (decision) {
            case AdjudicationDecision.Approved a -> {
                claim.approve(a.allowedAmount(), a.rationale(), by, now);
                yield "approved";
            }
            case AdjudicationDecision.Denied d -> {
                claim.deny("%s: %s".formatted(d.code(), d.reason()), by, now);
                yield "denied";
            }
            case AdjudicationDecision.NeedsReview r -> {
                claim.sendToReview(String.join("; ", r.reasons()), by, now);
                yield "review";
            }
        };

        meters.counter("claims.adjudication.decisions", "outcome", outcome).increment();
        log.info("Claim adjudicated claimNumber={} outcome={} status={}", claimNumber, outcome, claim.getStatus());
        return ClaimResponse.from(claim); // managed entity: dirty checking flushes it, no save() needed
    }

    /** Manual transition by an adjudicator (e.g. resolve a review, or record payment). */
    @Transactional
    public ClaimResponse changeStatus(String claimNumber, UpdateStatusRequest request) {
        Claim claim = load(claimNumber);
        ClaimStatus target = request.status();
        Instant now = clock.instant();
        String by = actor.name();

        // A SUBMITTED claim must go through the rules engine; a human can't bypass eligibility checks.
        if (claim.getStatus() == ClaimStatus.SUBMITTED && target != ClaimStatus.IN_REVIEW) {
            throw new InvalidStatusTransitionException(claim.getStatus(), target,
                    "A SUBMITTED claim must be adjudicated first (POST /adjudicate) or sent to IN_REVIEW");
        }

        switch (target) {
            case APPROVED -> claim.approve(
                    pricing.allowedAmount(claim, providers.findByNpi(claim.getProviderNpi())),
                    reasonOrDefault(request, "Approved on manual review"), by, now);
            case DENIED -> {
                if (request.reason() == null || request.reason().isBlank()) {
                    throw new BusinessRuleViolationException("REASON_REQUIRED", "A reason is required to deny a claim");
                }
                claim.deny(request.reason(), by, now);
            }
            case IN_REVIEW -> claim.sendToReview(reasonOrDefault(request, "Sent for manual review"), by, now);
            case PAID -> claim.markPaid(by, now);
            case SUBMITTED -> throw new InvalidStatusTransitionException(claim.getStatus(), target);
        }

        log.info("Claim status changed claimNumber={} status={} by={}", claimNumber, claim.getStatus(), by);
        return ClaimResponse.from(claim); // managed entity: dirty checking flushes it, no save() needed
    }

    private Claim load(String claimNumber) {
        return claims.findByClaimNumber(claimNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimNumber));
    }

    private static String reasonOrDefault(UpdateStatusRequest request, String fallback) {
        return request.reason() == null || request.reason().isBlank() ? fallback : request.reason();
    }

    /** e.g. CLM-2026-00000042. Backed by a DB sequence, so it is unique across service instances. */
    private String nextClaimNumber(Instant now) {
        int year = now.atZone(ZoneOffset.UTC).getYear();
        return "CLM-%d-%08d".formatted(year, claims.nextClaimSequence());
    }
}

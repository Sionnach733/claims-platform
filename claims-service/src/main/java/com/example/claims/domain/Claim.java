package com.example.claims.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root for a healthcare claim.
 *
 * <p>All state changes go through this class: lines keep the totals consistent, and every
 * status transition is checked against {@link ClaimStatus} and appended to the audit history
 * (which is also the event outbox) in the same transaction.
 */
@Entity
@Table(name = "claims")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", nullable = false, unique = true, length = 30)
    private String claimNumber;

    @Column(name = "member_id", nullable = false, length = 20)
    private String memberId;

    @Column(name = "provider_npi", nullable = false, length = 10)
    private String providerNpi;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status;

    @Column(name = "total_billed", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalBilled = BigDecimal.ZERO.setScale(2);

    @Column(name = "total_allowed", precision = 12, scale = 2)
    private BigDecimal totalAllowed;

    @Column(name = "total_paid", precision = 12, scale = 2)
    private BigDecimal totalPaid;

    @Column(name = "denial_reason")
    private String denialReason;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Version
    private long version;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<ClaimLine> lines = new ArrayList<>();

    // Append-only audit trail: cascade PERSIST/MERGE so new entries are saved, but never REMOVE.
    @OneToMany(mappedBy = "claim", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("id ASC")
    private List<ClaimStatusHistory> history = new ArrayList<>();

    protected Claim() {
        // for JPA
    }

    public Claim(String claimNumber, String memberId, String providerNpi, LocalDate serviceDate,
                 Instant submittedAt, String submittedBy) {
        this.claimNumber = claimNumber;
        this.memberId = memberId;
        this.providerNpi = providerNpi;
        this.serviceDate = serviceDate;
        this.submittedAt = submittedAt;
        this.status = ClaimStatus.SUBMITTED;
        history.add(new ClaimStatusHistory(this, null, ClaimStatus.SUBMITTED, "Claim submitted", submittedBy, submittedAt));
    }

    public void addLine(ClaimLine line) {
        if (status != ClaimStatus.SUBMITTED) {
            throw new IllegalStateException("Lines can only be added while the claim is SUBMITTED");
        }
        line.attachTo(this, lines.size() + 1);
        lines.add(line);
        totalBilled = totalBilled.add(line.getBilledAmount());
    }

    // ---- lifecycle -------------------------------------------------------------------------

    public void approve(BigDecimal allowedAmount, String reason, String actor, Instant at) {
        Objects.requireNonNull(allowedAmount, "allowedAmount");
        if (allowedAmount.signum() < 0 || allowedAmount.compareTo(totalBilled) > 0) {
            throw new IllegalArgumentException("Allowed amount must be between 0 and the billed amount");
        }
        transitionTo(ClaimStatus.APPROVED, reason, actor, at);
        this.totalAllowed = allowedAmount;
    }

    public void deny(String reason, String actor, Instant at) {
        Objects.requireNonNull(reason, "a denial must have a reason");
        transitionTo(ClaimStatus.DENIED, reason, actor, at);
        this.denialReason = reason;
        this.totalAllowed = BigDecimal.ZERO.setScale(2);
        this.totalPaid = BigDecimal.ZERO.setScale(2);
    }

    public void sendToReview(String reason, String actor, Instant at) {
        transitionTo(ClaimStatus.IN_REVIEW, reason, actor, at);
    }

    /** Simplified payment: the plan pays the full allowed amount (no member cost-share modelled). */
    public void markPaid(String actor, Instant at) {
        transitionTo(ClaimStatus.PAID, "Payment issued", actor, at);
        this.totalPaid = totalAllowed;
    }

    public void recordRiskScore(BigDecimal score) {
        this.riskScore = score;
    }

    private void transitionTo(ClaimStatus target, String reason, String actor, Instant at) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(status, target);
        }
        history.add(new ClaimStatusHistory(this, status, target, reason, actor, at));
        this.status = target;
    }

    // ---- accessors -------------------------------------------------------------------------

    public Long getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public String getMemberId() { return memberId; }
    public String getProviderNpi() { return providerNpi; }
    public LocalDate getServiceDate() { return serviceDate; }
    public Instant getSubmittedAt() { return submittedAt; }
    public ClaimStatus getStatus() { return status; }
    public BigDecimal getTotalBilled() { return totalBilled; }
    public BigDecimal getTotalAllowed() { return totalAllowed; }
    public BigDecimal getTotalPaid() { return totalPaid; }
    public String getDenialReason() { return denialReason; }
    public BigDecimal getRiskScore() { return riskScore; }
    public long getVersion() { return version; }
    public List<ClaimLine> getLines() { return Collections.unmodifiableList(lines); }
    public List<ClaimStatusHistory> getHistory() { return Collections.unmodifiableList(history); }
}

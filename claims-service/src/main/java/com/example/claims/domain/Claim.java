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

/**
 * Aggregate root for a healthcare claim. Lines are only modified through this class,
 * so totals always stay consistent with the lines.
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

    protected Claim() {
        // for JPA
    }

    public Claim(String claimNumber, String memberId, String providerNpi, LocalDate serviceDate, Instant submittedAt) {
        this.claimNumber = claimNumber;
        this.memberId = memberId;
        this.providerNpi = providerNpi;
        this.serviceDate = serviceDate;
        this.submittedAt = submittedAt;
        this.status = ClaimStatus.SUBMITTED;
    }

    public void addLine(ClaimLine line) {
        line.attachTo(this, lines.size() + 1);
        lines.add(line);
        totalBilled = totalBilled.add(line.getBilledAmount());
    }

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
}

package com.example.claims.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "claim_lines")
public class ClaimLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "procedure_code", nullable = false, length = 5)
    private String procedureCode;

    @Column(name = "diagnosis_code", nullable = false, length = 8)
    private String diagnosisCode;

    @Column(nullable = false)
    private int units;

    @Column(name = "billed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal billedAmount;

    protected ClaimLine() {
        // for JPA
    }

    public ClaimLine(String procedureCode, String diagnosisCode, int units, BigDecimal billedAmount) {
        this.procedureCode = procedureCode;
        this.diagnosisCode = diagnosisCode;
        this.units = units;
        this.billedAmount = billedAmount;
    }

    /** Called by {@link Claim#addLine} to keep both sides of the association consistent. */
    void attachTo(Claim claim, int lineNumber) {
        this.claim = claim;
        this.lineNumber = lineNumber;
    }

    public Long getId() { return id; }
    public Claim getClaim() { return claim; }
    public int getLineNumber() { return lineNumber; }
    public String getProcedureCode() { return procedureCode; }
    public String getDiagnosisCode() { return diagnosisCode; }
    public int getUnits() { return units; }
    public BigDecimal getBilledAmount() { return billedAmount; }
}

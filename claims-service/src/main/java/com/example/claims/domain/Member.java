package com.example.claims.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true, length = 20)
    private String memberId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "plan_code", nullable = false, length = 20)
    private String planCode;

    @Column(name = "coverage_start", nullable = false)
    private LocalDate coverageStart;

    @Column(name = "coverage_end")
    private LocalDate coverageEnd;

    protected Member() {
        // for JPA
    }

    public Member(String memberId, String firstName, String lastName, LocalDate dateOfBirth,
                  String planCode, LocalDate coverageStart, LocalDate coverageEnd) {
        this.memberId = memberId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.planCode = planCode;
        this.coverageStart = coverageStart;
        this.coverageEnd = coverageEnd;
    }

    /** True if the member had active coverage on the given date (coverage_end is inclusive). */
    public boolean isCoveredOn(LocalDate date) {
        return !date.isBefore(coverageStart) && (coverageEnd == null || !date.isAfter(coverageEnd));
    }

    public Long getId() { return id; }
    public String getMemberId() { return memberId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getPlanCode() { return planCode; }
    public LocalDate getCoverageStart() { return coverageStart; }
    public LocalDate getCoverageEnd() { return coverageEnd; }
}

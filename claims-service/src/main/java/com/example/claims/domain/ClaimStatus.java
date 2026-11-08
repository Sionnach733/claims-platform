package com.example.claims.domain;

/**
 * Claim lifecycle: SUBMITTED → IN_REVIEW → APPROVED | DENIED → PAID.
 * Transition rules are enforced in phase 3 (state machine).
 */
public enum ClaimStatus {
    SUBMITTED,
    IN_REVIEW,
    APPROVED,
    DENIED,
    PAID
}

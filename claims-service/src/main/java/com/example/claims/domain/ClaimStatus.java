package com.example.claims.domain;

import java.util.Set;

/**
 * Claim lifecycle state machine.
 *
 * <pre>
 *   SUBMITTED ──► IN_REVIEW ──► APPROVED ──► PAID
 *       │             │
 *       ├─────────────┴──► DENIED
 *       └──────────────────► APPROVED
 * </pre>
 *
 * DENIED and PAID are terminal.
 */
public enum ClaimStatus {
    SUBMITTED,
    IN_REVIEW,
    APPROVED,
    DENIED,
    PAID;

    public Set<ClaimStatus> allowedTransitions() {
        return switch (this) {
            case SUBMITTED -> Set.of(IN_REVIEW, APPROVED, DENIED);
            case IN_REVIEW -> Set.of(APPROVED, DENIED);
            case APPROVED -> Set.of(PAID);
            case DENIED, PAID -> Set.of();
        };
    }

    public boolean canTransitionTo(ClaimStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}

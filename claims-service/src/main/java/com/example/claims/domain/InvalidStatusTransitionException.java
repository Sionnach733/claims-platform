package com.example.claims.domain;

/** Maps to HTTP 409: the claim's current state does not allow the requested change. */
public class InvalidStatusTransitionException extends RuntimeException {

    private final ClaimStatus from;
    private final ClaimStatus to;

    public InvalidStatusTransitionException(ClaimStatus from, ClaimStatus to) {
        this(from, to, "Cannot move a claim from %s to %s".formatted(from, to));
    }

    public InvalidStatusTransitionException(ClaimStatus from, ClaimStatus to, String message) {
        super(message);
        this.from = from;
        this.to = to;
    }

    public ClaimStatus getFrom() { return from; }
    public ClaimStatus getTo() { return to; }
}

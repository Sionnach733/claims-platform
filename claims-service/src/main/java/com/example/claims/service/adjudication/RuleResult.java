package com.example.claims.service.adjudication;

/** Outcome of a single rule. Sealed so the engine's switch is checked for exhaustiveness by the compiler. */
public sealed interface RuleResult {

    RuleResult PASS = new Pass();

    record Pass() implements RuleResult {
    }

    /** Hard stop: the claim is denied. {@code code} is stable and machine-readable. */
    record Deny(String code, String reason) implements RuleResult {
    }

    /** Soft flag: a human must look at the claim before it can be approved. */
    record Review(String reason) implements RuleResult {
    }

    static RuleResult deny(String code, String reason) {
        return new Deny(code, reason);
    }

    static RuleResult review(String reason) {
        return new Review(reason);
    }
}

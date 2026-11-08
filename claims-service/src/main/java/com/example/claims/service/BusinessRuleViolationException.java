package com.example.claims.service;

/**
 * The request is well-formed but breaks a business rule (e.g. claim for an unknown member).
 * Maps to HTTP 422. {@code code} is a stable, machine-readable identifier for clients.
 */
public class BusinessRuleViolationException extends RuntimeException {

    private final String code;

    public BusinessRuleViolationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

package com.example.claims.service;

import org.springframework.stereotype.Component;

/**
 * Who is performing the current action, recorded in the audit trail.
 * Placeholder until authentication is added (phase 6), when it will read the Spring Security principal.
 */
@Component
public class CurrentActor {

    public static final String SYSTEM_ADJUDICATOR = "system:adjudication-engine";

    public String name() {
        return "anonymous";
    }
}

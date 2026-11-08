package com.example.claims.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Who is performing the current action, recorded in the audit trail: the authenticated user's name. */
@Component
public class CurrentActor {

    public static final String SYSTEM_ADJUDICATOR = "system:adjudication-engine";

    public String name() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }
        return auth.getName();
    }
}

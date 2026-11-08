package com.example.claims.security;

/** Application roles. Spring authorities carry the ROLE_ prefix; these constants don't. */
public final class Roles {

    /** Providers / intake staff: submit claims and view claims and reference data. */
    public static final String SUBMITTER = "SUBMITTER";

    /** Claims adjudicators: everything a submitter can do, plus adjudicate and change status. */
    public static final String ADJUDICATOR = "ADJUDICATOR";

    private Roles() {
    }
}

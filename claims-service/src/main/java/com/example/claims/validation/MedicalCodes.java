package com.example.claims.validation;

/** Format patterns for medical code sets (format only; code-set membership is not checked). */
public final class MedicalCodes {

    /** CPT/HCPCS Level I: 4 digits followed by a digit or a category II/III letter (e.g. 99213, 0001F, 0042T). */
    public static final String CPT_PATTERN = "^\\d{4}[0-9FTU]$";

    /** ICD-10-CM: letter, digit, alphanumeric, then an optional dot and 1–4 alphanumerics (e.g. J45.909, E11.9, M54.5). */
    public static final String ICD10_PATTERN = "^[A-TV-Z]\\d[0-9A-Z](\\.[0-9A-Z]{1,4})?$";

    /** Member identifiers issued by this plan: "AET" + 9 digits. */
    public static final String MEMBER_ID_PATTERN = "^AET\\d{9}$";

    private MedicalCodes() {
    }
}

package com.example.claims.observability;

import java.util.regex.Pattern;

/**
 * Defence in depth for logs. Code is written not to log PHI at all; this catches what slips through
 * (an exception message that echoes input, a third-party library's log line) before it reaches the log store.
 */
public final class PhiMasker {

    /** Plan member IDs: keep the prefix and last 3 digits so support can still correlate. */
    private static final Pattern MEMBER_ID = Pattern.compile("\\bAET\\d{6}(\\d{3})\\b");
    /** US Social Security numbers. */
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    /** Date-of-birth style fields, e.g. "dateOfBirth=1985-03-14" or "\"dob\":\"1985-03-14\"". */
    private static final Pattern DOB = Pattern.compile(
            "(?i)((?:date_?of_?birth|dob)\"?\\s*[=:]\\s*\"?)\\d{4}-\\d{2}-\\d{2}");

    private PhiMasker() {
    }

    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String masked = MEMBER_ID.matcher(value).replaceAll("AET******$1");
        masked = SSN.matcher(masked).replaceAll("***-**-****");
        return DOB.matcher(masked).replaceAll("$1****-**-**");
    }
}

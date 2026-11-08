package com.example.claims.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates an NPI using the CMS algorithm: the Luhn check applied to the 9-digit base
 * prefixed with the health-industry constant "80840".
 */
public class NpiValidator implements ConstraintValidator<Npi, String> {

    private static final String HEALTH_INDUSTRY_PREFIX = "80840";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || isValidNpi(value);
    }

    public static boolean isValidNpi(String npi) {
        if (npi == null || !npi.matches("\\d{10}")) {
            return false;
        }
        return luhnValid(HEALTH_INDUSTRY_PREFIX + npi);
    }

    private static boolean luhnValid(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}

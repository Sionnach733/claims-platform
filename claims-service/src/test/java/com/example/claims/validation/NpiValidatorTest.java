package com.example.claims.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NpiValidatorTest {

    private final NpiValidator validator = new NpiValidator();

    @ParameterizedTest
    @ValueSource(strings = {"1234567893", "1987654328", "1456789019", "1678901236", "1321098760"})
    void acceptsNpisWithValidCheckDigit(String npi) {
        assertThat(NpiValidator.isValidNpi(npi)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234567890",   // wrong check digit
            "1234567894",   // wrong check digit
            "123456789",    // too short
            "12345678933",  // too long
            "12345678a3",   // non-digit
            " 1234567893"   // whitespace
    })
    void rejectsMalformedOrInvalidNpis(String npi) {
        assertThat(NpiValidator.isValidNpi(npi)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void staticCheckRejectsNullAndEmpty(String npi) {
        assertThat(NpiValidator.isValidNpi(npi)).isFalse();
    }

    @Test
    void constraintTreatsNullAsValidSoNotBlankOwnsRequiredness() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}

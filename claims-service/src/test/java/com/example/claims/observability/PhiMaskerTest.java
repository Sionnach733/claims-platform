package com.example.claims.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class PhiMaskerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Member 'AET100000001' does not exist        | Member 'AET******001' does not exist",
            "members AET100000001,AET123456789           | members AET******001,AET******789",
            "ssn=123-45-6789                             | ssn=***-**-****",
            "dateOfBirth=1985-03-14                      | dateOfBirth=****-**-**",
            "{\"dob\":\"1985-03-14\"}                    | {\"dob\":\"****-**-**\"}",
            "serviceDate=2026-11-01 claim CLM-2026-00000001 | serviceDate=2026-11-01 claim CLM-2026-00000001",
            "NPI 1234567893                              | NPI 1234567893",
    })
    void masksIdentifiersButLeavesOperationalDataAlone(String input, String expected) {
        assertThat(PhiMasker.mask(input.strip())).isEqualTo(expected.strip());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nullAndEmptyPassThrough(String value) {
        assertThat(PhiMasker.mask(value)).isEqualTo(value);
    }
}

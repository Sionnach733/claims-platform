package com.example.claims.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CorrelationIdTest {

    @Test
    void acceptsWellFormedIncomingId() {
        assertThat(CorrelationId.acceptOrGenerate("ui-7f3c2a9e-41d0")).isEqualTo("ui-7f3c2a9e-41d0");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "has spaces in it", "inject\r\nX-Evil: 1", "<script>alert(1)</script>"})
    void replacesMissingOrUnsafeIdsWithAFreshUuid(String incoming) {
        String id = CorrelationId.acceptOrGenerate(incoming);

        assertThat(id).matches("[0-9a-f-]{36}").isNotEqualTo(incoming);
    }

    @Test
    void rejectsOverlongIds() {
        assertThat(CorrelationId.acceptOrGenerate("a".repeat(65))).hasSize(36);
    }
}

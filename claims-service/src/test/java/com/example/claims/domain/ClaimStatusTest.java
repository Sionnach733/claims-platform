package com.example.claims.domain;

import static com.example.claims.domain.ClaimStatus.APPROVED;
import static com.example.claims.domain.ClaimStatus.DENIED;
import static com.example.claims.domain.ClaimStatus.IN_REVIEW;
import static com.example.claims.domain.ClaimStatus.PAID;
import static com.example.claims.domain.ClaimStatus.SUBMITTED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ClaimStatusTest {

    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(SUBMITTED, Set.of(IN_REVIEW, APPROVED, DENIED)),
                Arguments.of(IN_REVIEW, Set.of(APPROVED, DENIED)),
                Arguments.of(APPROVED, Set.of(PAID)),
                Arguments.of(DENIED, Set.of()),
                Arguments.of(PAID, Set.of()));
    }

    /** Checks every (from, to) pair, so a new transition can't sneak in untested. */
    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void exactlyTheDocumentedTransitionsAreAllowed(ClaimStatus from, Set<ClaimStatus> allowed) {
        for (ClaimStatus to : ClaimStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(allowed.contains(to));
        }
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"DENIED", "PAID"})
    void deniedAndPaidAreTerminal(ClaimStatus status) {
        assertThat(status.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ClaimStatus.class)
    void noStatusCanTransitionToItselfOrBackToSubmitted(ClaimStatus status) {
        assertThat(status.allowedTransitions()).doesNotContain(status, SUBMITTED);
        assertThat(EnumSet.allOf(ClaimStatus.class)).containsAll(status.allowedTransitions());
    }
}

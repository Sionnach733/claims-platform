package com.example.claims.api.dto;

import com.example.claims.domain.Member;
import java.time.LocalDate;

/**
 * A member as listed for picking one on a claim. Minimum necessary: no date of birth, which only the
 * single-member lookup returns.
 */
public record MemberSummaryResponse(String memberId, String firstName, String lastName, String planCode,
                                    LocalDate coverageStart, LocalDate coverageEnd) {

    public static MemberSummaryResponse from(Member m) {
        return new MemberSummaryResponse(m.getMemberId(), m.getFirstName(), m.getLastName(), m.getPlanCode(),
                m.getCoverageStart(), m.getCoverageEnd());
    }
}

package com.example.claims.api.dto;

import com.example.claims.domain.Member;
import java.time.LocalDate;

public record MemberResponse(String memberId, String firstName, String lastName, LocalDate dateOfBirth,
                             String planCode, LocalDate coverageStart, LocalDate coverageEnd) {

    public static MemberResponse from(Member m) {
        return new MemberResponse(m.getMemberId(), m.getFirstName(), m.getLastName(), m.getDateOfBirth(),
                m.getPlanCode(), m.getCoverageStart(), m.getCoverageEnd());
    }
}

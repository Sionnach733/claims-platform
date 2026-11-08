package com.example.claims.repository;

import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimStatus;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/** Composable, null-safe filters for the claim search API. A null argument means "no filter". */
public final class ClaimSpecifications {

    private ClaimSpecifications() {
    }

    public static Specification<Claim> search(String memberId, ClaimStatus status, LocalDate from, LocalDate to) {
        return Specification.allOf(
                memberIdEquals(memberId),
                statusEquals(status),
                serviceDateOnOrAfter(from),
                serviceDateOnOrBefore(to));
    }

    static Specification<Claim> memberIdEquals(String memberId) {
        return (root, query, cb) -> memberId == null ? null : cb.equal(root.get("memberId"), memberId);
    }

    static Specification<Claim> statusEquals(ClaimStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    static Specification<Claim> serviceDateOnOrAfter(LocalDate from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("serviceDate"), from);
    }

    static Specification<Claim> serviceDateOnOrBefore(LocalDate to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("serviceDate"), to);
    }
}

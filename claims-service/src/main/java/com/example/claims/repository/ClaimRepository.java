package com.example.claims.repository;

import com.example.claims.domain.Claim;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    /** Detail view: fetch lines in the same query to avoid a lazy-load round trip. */
    @EntityGraph(attributePaths = "lines")
    Optional<Claim> findByClaimNumber(String claimNumber);

    @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimSequence();

    /**
     * Claim numbers of earlier (lower id), non-denied claims with the same member, provider and
     * service date that share at least one procedure code. Served by idx_claims_member_service_date.
     */
    @Query("""
            SELECT DISTINCT c.claimNumber FROM Claim c JOIN c.lines l
            WHERE c.id < :claimId
              AND c.memberId = :memberId
              AND c.providerNpi = :providerNpi
              AND c.serviceDate = :serviceDate
              AND c.status <> com.example.claims.domain.ClaimStatus.DENIED
              AND l.procedureCode IN :procedureCodes
            ORDER BY c.claimNumber
            """)
    List<String> findEarlierDuplicate(@Param("claimId") Long claimId,
                                      @Param("memberId") String memberId,
                                      @Param("providerNpi") String providerNpi,
                                      @Param("serviceDate") LocalDate serviceDate,
                                      @Param("procedureCodes") Collection<String> procedureCodes);
}

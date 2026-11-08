package com.example.claims.repository;

import com.example.claims.domain.Claim;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    /** Detail view: fetch lines in the same query to avoid a lazy-load round trip. */
    @EntityGraph(attributePaths = "lines")
    Optional<Claim> findByClaimNumber(String claimNumber);

    @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimSequence();
}

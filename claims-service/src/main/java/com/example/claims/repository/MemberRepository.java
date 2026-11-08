package com.example.claims.repository;

import com.example.claims.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByMemberId(String memberId);

    boolean existsByMemberId(String memberId);
}

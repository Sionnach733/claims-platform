package com.example.claims.service;

import com.example.claims.domain.Member;
import com.example.claims.domain.Provider;
import com.example.claims.repository.MemberRepository;
import com.example.claims.repository.ProviderRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only access to members and providers (seeded via Flyway). */
@Service
@Transactional(readOnly = true)
public class ReferenceDataService {

    private final MemberRepository members;
    private final ProviderRepository providers;

    public ReferenceDataService(MemberRepository members, ProviderRepository providers) {
        this.members = members;
        this.providers = providers;
    }

    public Member member(String memberId) {
        return members.findByMemberId(memberId).orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
    }

    public Provider provider(String npi) {
        return providers.findByNpi(npi).orElseThrow(() -> new ResourceNotFoundException("Provider", npi));
    }

    public List<Provider> providers() {
        return providers.findAll(Sort.by("name"));
    }
}

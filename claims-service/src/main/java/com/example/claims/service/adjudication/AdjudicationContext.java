package com.example.claims.service.adjudication;

import com.example.claims.domain.Claim;
import com.example.claims.domain.Member;
import com.example.claims.domain.Provider;
import java.util.Optional;

/** Everything a rule may inspect. Lookups are done once up front, not by each rule. */
public record AdjudicationContext(Claim claim, Optional<Member> member, Optional<Provider> provider) {
}

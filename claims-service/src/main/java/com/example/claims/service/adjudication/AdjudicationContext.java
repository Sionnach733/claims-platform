package com.example.claims.service.adjudication;

import com.example.claims.domain.Claim;
import com.example.claims.domain.Member;
import com.example.claims.domain.Provider;
import com.example.claims.fraud.FraudAssessment;
import java.util.Objects;
import java.util.Optional;

/** Everything a rule may inspect. Lookups and remote calls happen once up front, never inside rules. */
public record AdjudicationContext(Claim claim, Optional<Member> member, Optional<Provider> provider,
                                  FraudAssessment fraud) {

    public AdjudicationContext {
        Objects.requireNonNull(fraud, "fraud assessment is required (use Unavailable if there is none)");
    }
}

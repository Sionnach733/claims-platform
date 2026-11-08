package com.example.claims.service.adjudication;

import com.example.claims.domain.Claim;
import com.example.claims.domain.Provider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Computes the allowed amount: billed × network rate, rounded to cents with banker's rounding
 * (HALF_EVEN), which avoids systematic upward bias across millions of claims.
 */
@Component
public class PricingPolicy {

    private final AdjudicationProperties properties;

    public PricingPolicy(AdjudicationProperties properties) {
        this.properties = properties;
    }

    public BigDecimal allowedAmount(Claim claim, Optional<Provider> provider) {
        return claim.getTotalBilled().multiply(rateFor(provider)).setScale(2, RoundingMode.HALF_EVEN);
    }

    /** Unknown providers are priced as out-of-network (most conservative). */
    public BigDecimal rateFor(Optional<Provider> provider) {
        return provider.filter(Provider::isInNetwork).isPresent()
                ? properties.inNetworkRate()
                : properties.outOfNetworkRate();
    }
}

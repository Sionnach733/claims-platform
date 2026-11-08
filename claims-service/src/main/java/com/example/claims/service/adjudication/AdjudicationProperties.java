package com.example.claims.service.adjudication;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Business parameters for adjudication, externalised so they can change per environment/plan
 * without a code change. Validated at startup: a bad rate fails fast instead of mis-paying claims.
 */
@Validated
@ConfigurationProperties("app.adjudication")
public record AdjudicationProperties(
        @NotNull @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal inNetworkRate,
        @NotNull @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal outOfNetworkRate,
        @NotNull @DecimalMin("0.01") BigDecimal manualReviewThreshold) {
}

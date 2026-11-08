package com.example.claims.fraud;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for calling fraud-service. Timeouts are deliberately tight: adjudication must stay fast,
 * and a slow fraud check degrades to manual review rather than blocking the caller.
 */
@Validated
@ConfigurationProperties("app.fraud-service")
public record FraudServiceProperties(
        @NotNull URI baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(0) @Max(100) int reviewThreshold,
        @Valid @NotNull Retry retry,
        @Valid @NotNull CircuitBreaker circuitBreaker) {

    public record Retry(@Min(1) @Max(5) int maxAttempts, @NotNull Duration backoff) {
    }

    public record CircuitBreaker(
            @Min(1) @Max(100) float failureRateThreshold,
            @Min(1) int slidingWindowSize,
            @Min(1) int minimumNumberOfCalls,
            @NotNull Duration waitDurationInOpenState,
            @Min(1) int permittedCallsInHalfOpenState) {
    }
}

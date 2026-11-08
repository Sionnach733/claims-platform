package com.example.claims.fraud;

import com.example.claims.observability.CorrelationIdPropagator;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class FraudClientConfig {

    static final String NAME = "fraudService";

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meters) {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meters);
        return registry;
    }

    @Bean
    RetryRegistry retryRegistry(MeterRegistry meters) {
        RetryRegistry registry = RetryRegistry.ofDefaults();
        TaggedRetryMetrics.ofRetryRegistry(registry).bindTo(meters);
        return registry;
    }

    @Bean
    FraudClient fraudClient(RestClient.Builder builder, FraudServiceProperties props,
                            CircuitBreakerRegistry breakers, RetryRegistry retries) {
        var cb = props.circuitBreaker();
        CircuitBreaker circuitBreaker = breakers.circuitBreaker(NAME, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(cb.slidingWindowSize())
                .minimumNumberOfCalls(cb.minimumNumberOfCalls())
                .failureRateThreshold(cb.failureRateThreshold())
                .waitDurationInOpenState(cb.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(cb.permittedCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .ignoreExceptions(HttpClientErrorException.class) // 4xx: our bug, not fraud-service's health
                .build());

        Retry retry = retries.retry(NAME, RetryConfig.custom()
                .maxAttempts(props.retry().maxAttempts())
                .waitDuration(props.retry().backoff())
                .retryExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .ignoreExceptions(HttpClientErrorException.class, CallNotPermittedException.class)
                .build());

        // HTTP/1.1 explicitly: the JDK client defaults to HTTP/2 and, over plain http, attempts an h2c
        // upgrade on every request, which some servers/proxies (e.g. Jetty, many sidecars) reject.
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.connectTimeout())
                .build());
        requestFactory.setReadTimeout(props.readTimeout());

        RestClient http = builder
                .baseUrl(props.baseUrl().toString())
                .requestFactory(requestFactory)
                .defaultHeader("X-API-Key", props.apiKey())
                .requestInterceptor(new CorrelationIdPropagator())
                .build();
        return new HttpFraudClient(http, circuitBreaker, retry);
    }
}

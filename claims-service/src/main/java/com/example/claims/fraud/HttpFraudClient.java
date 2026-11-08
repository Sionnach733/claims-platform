package com.example.claims.fraud;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls fraud-service with a timeout, retry and circuit breaker:
 * <pre>retry( circuitBreaker( HTTP call with connect/read timeouts ) )</pre>
 * <ul>
 *   <li>Timeouts bound how long one attempt can take.</li>
 *   <li>Retry absorbs brief blips (I/O errors, 5xx). It never retries 4xx, which would fail again.</li>
 *   <li>The circuit breaker stops calling a service that is clearly down, so a fraud-service outage
 *       costs microseconds per claim instead of a full timeout.</li>
 * </ul>
 * Any failure degrades to {@link FraudAssessment.Unavailable}. Exceptions never reach the caller.
 */
public class HttpFraudClient implements FraudClient {

    private static final Logger log = LoggerFactory.getLogger(HttpFraudClient.class);

    private final RestClient http;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public HttpFraudClient(RestClient http, CircuitBreaker circuitBreaker, Retry retry) {
        this.http = http;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    @Override
    public FraudAssessment assess(FraudScoreRequest request) {
        Supplier<FraudScoreResponse> call = () -> http.post()
                .uri("/v1/score")
                .body(request)
                .retrieve()
                .body(FraudScoreResponse.class);
        Supplier<FraudScoreResponse> resilient = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, call));
        try {
            FraudScoreResponse response = resilient.get();
            if (response == null) {
                return new FraudAssessment.Unavailable("Empty response from fraud-service");
            }
            return response.toAssessment();
        } catch (CallNotPermittedException e) {
            return unavailable(request, "circuit breaker open");
        } catch (HttpClientErrorException e) {
            // A 4xx means our request is wrong: a bug to fix, not an outage. Log it loudly.
            log.error("fraud-service rejected request claimNumber={} status={}", request.claimNumber(), e.getStatusCode());
            return new FraudAssessment.Unavailable("Fraud screening rejected the request (" + e.getStatusCode().value() + ")");
        } catch (RestClientException e) {
            Throwable root = e.getMostSpecificCause();
            log.debug("fraud-service call failed", e);
            return unavailable(request, e.getClass().getSimpleName() + ": " + root.getClass().getSimpleName());
        }
    }

    private FraudAssessment unavailable(FraudScoreRequest request, String why) {
        log.warn("fraud-service unavailable claimNumber={} reason={} breakerState={}",
                request.claimNumber(), why, circuitBreaker.getState());
        return new FraudAssessment.Unavailable("Fraud screening unavailable (" + why + ")");
    }
}

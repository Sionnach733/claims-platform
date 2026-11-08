package com.example.claims.fraud;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

/** Exercises timeouts, retry and the circuit breaker over a real HTTP socket (WireMock). */
class HttpFraudClientTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final String SCORED = """
            { "claimNumber": "CLM-2026-00000001", "score": 82, "riskBand": "HIGH", "modelVersion": "rules-test",
              "scoredAt": "2026-11-08T12:00:00Z", "someNewField": true,
              "reasons": [ { "code": "EXCESSIVE_UNITS", "description": "12 units", "weight": 20 } ] }
            """;

    private final FraudScoreRequest request = new FraudScoreRequest("CLM-2026-00000001", "AET100000001",
            "1234567893", LocalDate.of(2026, 11, 1),
            List.of(new FraudScoreRequest.Line("99213", "J45.909", 1, new BigDecimal("125.50"))));

    private FraudClient client;
    private CircuitBreakerRegistry breakers;

    @BeforeEach
    void setUp() {
        var props = new FraudServiceProperties(URI.create(wm.baseUrl()), Duration.ofMillis(300),
                Duration.ofMillis(300), 70,
                new FraudServiceProperties.Retry(2, Duration.ofMillis(10)),
                new FraudServiceProperties.CircuitBreaker(50, 4, 4, Duration.ofMinutes(1), 1), "test-api-key");
        var meters = new SimpleMeterRegistry();
        var config = new FraudClientConfig();
        breakers = config.circuitBreakerRegistry(meters);
        client = config.fraudClient(RestClient.builder(), props, breakers, config.retryRegistry(meters));
    }

    @Test
    void successfulCallIsMappedToScoredAndToleratesUnknownFields() {
        wm.stubFor(post("/v1/score").willReturn(okJson(SCORED)));

        FraudAssessment result = client.assess(request);

        assertThat(result).isEqualTo(new FraudAssessment.Scored(82, "HIGH",
                List.of("EXCESSIVE_UNITS: 12 units"), "rules-test"));
        wm.verify(postRequestedFor(urlEqualTo("/v1/score"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(matchingJsonPath("$.lines[0].billedAmount", equalTo("125.5")))
                .withRequestBody(matchingJsonPath("$.serviceDate", equalTo("2026-11-01"))));
    }

    @Test
    void transientServerErrorIsRetriedOnceThenSucceeds() {
        wm.stubFor(post("/v1/score").inScenario("blip").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"));
        wm.stubFor(post("/v1/score").inScenario("blip").whenScenarioStateIs("recovered")
                .willReturn(okJson(SCORED)));

        assertThat(client.assess(request)).isInstanceOf(FraudAssessment.Scored.class);
        wm.verify(2, postRequestedFor(urlEqualTo("/v1/score")));
    }

    @Test
    void persistentServerErrorDegradesToUnavailableAfterRetries() {
        wm.stubFor(post("/v1/score").willReturn(aResponse().withStatus(500)));

        FraudAssessment result = client.assess(request);

        assertThat(result).isInstanceOf(FraudAssessment.Unavailable.class);
        wm.verify(2, postRequestedFor(urlEqualTo("/v1/score")));
    }

    @Test
    void clientErrorIsNotRetried() {
        wm.stubFor(post("/v1/score").willReturn(aResponse().withStatus(422)));

        FraudAssessment result = client.assess(request);

        assertThat(result).isInstanceOfSatisfying(FraudAssessment.Unavailable.class, u ->
                assertThat(u.reason()).contains("rejected the request (422)"));
        wm.verify(1, postRequestedFor(urlEqualTo("/v1/score")));
    }

    @Test
    void slowResponseTimesOutInsteadOfBlocking() {
        wm.stubFor(post("/v1/score").willReturn(okJson(SCORED).withFixedDelay(2_000)));

        long start = System.nanoTime();
        FraudAssessment result = client.assess(request);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(result).isInstanceOf(FraudAssessment.Unavailable.class);
        assertThat(elapsed).isLessThan(Duration.ofMillis(1_500)); // 2 attempts × 300ms read timeout, not 2 × 2s
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndStopsCallingTheService() {
        wm.stubFor(post("/v1/score").willReturn(aResponse().withStatus(503)));

        client.assess(request); // 2 failed attempts
        client.assess(request); // 2 more: window of 4 is now 100% failed → OPEN
        int callsBeforeOpen = wm.getAllServeEvents().size();

        FraudAssessment result = client.assess(request);

        assertThat(breakers.circuitBreaker(FraudClientConfig.NAME).getState().name()).isEqualTo("OPEN");
        assertThat(result).isInstanceOfSatisfying(FraudAssessment.Unavailable.class, u ->
                assertThat(u.reason()).contains("circuit breaker open"));
        assertThat(wm.getAllServeEvents()).hasSize(callsBeforeOpen); // no HTTP call made
    }

    @Test
    void connectionRefusedDegradesToUnavailable() {
        var props = new FraudServiceProperties(URI.create("http://localhost:1"), Duration.ofMillis(200),
                Duration.ofMillis(200), 70, new FraudServiceProperties.Retry(1, Duration.ofMillis(1)),
                new FraudServiceProperties.CircuitBreaker(50, 4, 4, Duration.ofMinutes(1), 1), "test-api-key");
        var meters = new SimpleMeterRegistry();
        var config = new FraudClientConfig();
        FraudClient unreachable = config.fraudClient(RestClient.builder(), props,
                config.circuitBreakerRegistry(meters), RetryRegistry.ofDefaults());

        assertThat(unreachable.assess(request)).isInstanceOf(FraudAssessment.Unavailable.class);
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder okJson(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}

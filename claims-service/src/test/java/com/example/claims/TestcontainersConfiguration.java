package com.example.claims;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real PostgreSQL and LocalStack (SQS) for tests. Same images as docker-compose. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String QUEUE_NAME = "claim-status-events";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }

    @Bean
    LocalStackContainer localStackContainer() throws IOException, InterruptedException {
        LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                .withServices("sqs");
        localStack.start();
        localStack.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", QUEUE_NAME);
        return localStack;
    }

    @Bean
    DynamicPropertyRegistrar awsProperties(LocalStackContainer localStack) {
        return registry -> {
            registry.add("app.aws.endpoint", () -> localStack.getEndpoint().toString());
            registry.add("app.aws.region", localStack::getRegion);
        };
    }

    /** Billed amount on a line that makes the fraud-service stub return a HIGH score. */
    public static final String HIGH_RISK_AMOUNT = "777.77";
    /** Billed amount on a line that makes the fraud-service stub fail with 503. */
    public static final String FRAUD_OUTAGE_AMOUNT = "666.66";

    /** Stand-in for fraud-service (contract stub), so claims-service tests don't need the Python service. */
    @Bean(destroyMethod = "stop")
    WireMockServer fraudServiceStub() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        server.stubFor(post("/v1/score").atPriority(10).willReturn(score(12, "LOW", "[]")));
        server.stubFor(post("/v1/score").atPriority(1)
                .withRequestBody(matchingJsonPath("$.lines[?(@.billedAmount == " + HIGH_RISK_AMOUNT + ")]"))
                .willReturn(score(88, "HIGH",
                        "[{\"code\":\"PRICE_ABOVE_BENCHMARK\",\"description\":\"far above benchmark\",\"weight\":25}]")));
        server.stubFor(post("/v1/score").atPriority(1)
                .withRequestBody(matchingJsonPath("$.lines[?(@.billedAmount == " + FRAUD_OUTAGE_AMOUNT + ")]"))
                .willReturn(aResponse().withStatus(503)));
        return server;
    }

    @Bean
    DynamicPropertyRegistrar fraudServiceProperties(WireMockServer fraudServiceStub) {
        return registry -> registry.add("app.fraud-service.base-url", fraudServiceStub::baseUrl);
    }

    private static ResponseDefinitionBuilder score(int score, String band, String reasons) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                {"claimNumber":"ignored","score":%d,"riskBand":"%s","reasons":%s,"modelVersion":"stub",
                 "scoredAt":"2026-11-08T12:00:00Z"}""".formatted(score, band, reasons));
    }
}

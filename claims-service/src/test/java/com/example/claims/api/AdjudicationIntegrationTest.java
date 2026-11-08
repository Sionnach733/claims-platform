package com.example.claims.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.claims.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * End-to-end adjudication against real PostgreSQL + LocalStack SQS. Each test uses a unique service
 * date so the duplicate-claim rule never matches claims created by other tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdjudicationIntegrationTest {

    private static final AtomicInteger DAY_OFFSET = new AtomicInteger(40);

    private static final String ACTIVE_MEMBER = "AET100000001";
    private static final String LAPSED_MEMBER = "AET100000004";
    private static final String IN_NETWORK = "1234567893";
    private static final String OUT_OF_NETWORK = "1678901236";
    private static final String UNKNOWN_PROVIDER = "1111111112";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SqsClient sqs;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void cleanInNetworkClaimIsAutoApprovedAt80PercentThenPaid() throws Exception {
        String claim = submit(ACTIVE_MEMBER, IN_NETWORK, uniqueDate(), "99213", "125.50");

        adjudicate(claim)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.totalAllowed").value(100.40))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].changedBy").value("system:adjudication-engine"));

        changeStatus(claim, "PAID", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.totalPaid").value(100.40));
    }

    @Test
    void outOfNetworkClaimIsPricedAt50Percent() throws Exception {
        String claim = submit(ACTIVE_MEMBER, OUT_OF_NETWORK, uniqueDate(), "71046", "300.00");

        adjudicate(claim)
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.totalAllowed").value(150.00));
    }

    @Test
    void lapsedCoverageIsDenied() throws Exception {
        String claim = submit(LAPSED_MEMBER, IN_NETWORK, uniqueDate(), "99213", "100.00");

        adjudicate(claim)
                .andExpect(jsonPath("$.status").value("DENIED"))
                .andExpect(jsonPath("$.denialReason").value(startsWith("COVERAGE_INACTIVE")))
                .andExpect(jsonPath("$.totalPaid").value(0));
    }

    @Test
    void unknownProviderIsDenied() throws Exception {
        String claim = submit(ACTIVE_MEMBER, UNKNOWN_PROVIDER, uniqueDate(), "99213", "100.00");

        adjudicate(claim)
                .andExpect(jsonPath("$.status").value("DENIED"))
                .andExpect(jsonPath("$.denialReason").value(startsWith("PROVIDER_UNKNOWN")));
    }

    @Test
    void secondIdenticalClaimIsDeniedAsDuplicateOfTheFirst() throws Exception {
        LocalDate date = uniqueDate();
        String first = submit(ACTIVE_MEMBER, IN_NETWORK, date, "99214", "150.00");
        String second = submit(ACTIVE_MEMBER, IN_NETWORK, date, "99214", "150.00");

        // Adjudicate the later one first: still the duplicate, because only *earlier* claims count.
        adjudicate(second)
                .andExpect(jsonPath("$.status").value("DENIED"))
                .andExpect(jsonPath("$.denialReason").value("DUPLICATE_CLAIM: Duplicate of claim " + first));
        adjudicate(first).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void highValueClaimGoesToReviewThenCanBeApprovedManually() throws Exception {
        String claim = submit(ACTIVE_MEMBER, IN_NETWORK, uniqueDate(), "27447", "6000.00");

        adjudicate(claim)
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.totalAllowed").doesNotExist());

        changeStatus(claim, "APPROVED", "Operative report reviewed")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.totalAllowed").value(4800.00))
                .andExpect(jsonPath("$.history[2].reason").value("Operative report reviewed"));
    }

    @Test
    void cleanClaimRecordsTheFraudScore() throws Exception {
        String claim = submit(ACTIVE_MEMBER, IN_NETWORK, uniqueDate(), "99213", "110.00");

        adjudicate(claim)
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.riskScore").value(12));
    }

    @Test
    void highFraudScoreGoesToManualReview() throws Exception {
        String claim = submit(ACTIVE_MEMBER, IN_NETWORK, uniqueDate(), "99213",
                TestcontainersConfiguration.HIGH_RISK_AMOUNT);

        adjudicate(claim)
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.riskScore").value(88))
                .andExpect(jsonPath("$.history[1].reason").value(startsWith("Fraud risk score 88 (HIGH)")));
    }

    @Test
    void fraudServiceOutageDegradesToManualReviewInsteadOfFailing() throws Exception {
        String claim = submit(ACTIVE_MEMBER, IN_NETWORK, uniqueDate(), "99213",
                TestcontainersConfiguration.FRAUD_OUTAGE_AMOUNT);

        adjudicate(claim)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.riskScore").doesNotExist())
                .andExpect(jsonPath("$.history[1].reason").value(startsWith("Fraud screening unavailable")));
    }

    @Test
    void illegalTransitionsAreRejectedWith409() throws Exception {
        String claim = submit(ACTIVE_MEMBER, IN_NETWORK, uniqueDate(), "99213", "80.00");

        changeStatus(claim, "PAID", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("SUBMITTED"));

        adjudicate(claim).andExpect(jsonPath("$.status").value("APPROVED"));

        adjudicate(claim).andExpect(status().isConflict());
        changeStatus(claim, "DENIED", "changed my mind")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.allowedTransitions[0]").value("PAID"));
    }

    @Test
    void adjudicatingAnUnknownClaimIs404() throws Exception {
        adjudicate("CLM-1999-99999999").andExpect(status().isNotFound());
    }

    @Test
    void everyStatusChangeIsPublishedToSqsExactlyOncePerEvent() throws Exception {
        String claim = submit(ACTIVE_MEMBER, IN_NETWORK, uniqueDate(), "99213", "90.00");
        adjudicate(claim).andExpect(jsonPath("$.status").value("APPROVED"));
        changeStatus(claim, "PAID", null).andExpect(status().isOk());

        String queueUrl = sqs.getQueueUrl(b -> b.queueName(TestcontainersConfiguration.QUEUE_NAME)).queueUrl();
        List<String> bodiesForClaim = new ArrayList<>();

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            for (Message m : sqs.receiveMessage(b -> b.queueUrl(queueUrl).maxNumberOfMessages(10)
                    .waitTimeSeconds(1).messageAttributeNames("All")).messages()) {
                if (m.body().contains(claim)) {
                    bodiesForClaim.add(m.body());
                }
                sqs.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(m.receiptHandle()));
            }
            assertThat(bodiesForClaim).hasSize(3);
        });

        List<String> toStatuses = bodiesForClaim.stream().map(b -> JsonPath.<String>read(b, "$.toStatus")).toList();
        assertThat(toStatuses).containsExactlyInAnyOrder("SUBMITTED", "APPROVED", "PAID");
        assertThat(bodiesForClaim).allSatisfy(b -> assertThat(b)
                .contains("\"eventType\":\"ClaimStatusChanged\"")
                .doesNotContain(ACTIVE_MEMBER));
        assertThat(bodiesForClaim.stream().map(b -> JsonPath.<String>read(b, "$.eventId")).distinct()).hasSize(3);

        Long unpublished = jdbc.sql("""
                        SELECT count(*) FROM claim_status_history h JOIN claims c ON c.id = h.claim_id
                        WHERE c.claim_number = :n AND h.published_at IS NULL""")
                .param("n", claim).query(Long.class).single();
        assertThat(unpublished).isZero();
    }

    // ---- helpers --------------------------------------------------------------------------

    private static LocalDate uniqueDate() {
        return LocalDate.now().minusDays(DAY_OFFSET.getAndIncrement());
    }

    private String submit(String member, String npi, LocalDate date, String cpt, String amount) throws Exception {
        String body = """
                { "memberId": "%s", "providerNpi": "%s", "serviceDate": "%s",
                  "lines": [ { "procedureCode": "%s", "diagnosisCode": "M17.11", "units": 1, "billedAmount": %s } ] }
                """.formatted(member, npi, date, cpt, amount);
        String response = mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.claimNumber");
    }

    private ResultActions adjudicate(String claimNumber) throws Exception {
        return mvc.perform(post("/api/v1/claims/{n}/adjudicate", claimNumber));
    }

    private ResultActions changeStatus(String claimNumber, String target, String reason) throws Exception {
        String body = reason == null
                ? "{\"status\":\"%s\"}".formatted(target)
                : "{\"status\":\"%s\",\"reason\":\"%s\"}".formatted(target, reason);
        return mvc.perform(patch("/api/v1/claims/{n}/status", claimNumber)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}

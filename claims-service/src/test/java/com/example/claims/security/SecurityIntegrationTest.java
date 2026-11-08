package com.example.claims.security;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.claims.TestcontainersConfiguration;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Real tokens from the login endpoint, through the real filter chain, against the real database. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WireMockServer fraudServiceStub;

    @Test
    void apiRequiresAToken() throws Exception {
        mvc.perform(get("/api/v1/claims"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void wrongPasswordIs401WithoutSayingWhichPartWasWrong() throws Exception {
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"adjudicator\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = login("adjudicator", "adjudicator-pass");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mvc.perform(get("/api/v1/claims").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenCarriesRolesAndExpiry() throws Exception {
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"submitter\",\"password\":\"submitter-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.roles.length()").value(1))   // no FACTOR_* authorities leaking in
                .andExpect(jsonPath("$.roles[0]").value("SUBMITTER"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void submitterCanSubmitAndViewButNotAdjudicate() throws Exception {
        String submitter = login("submitter", "submitter-pass");
        String claimNumber = submitClaim(submitter);

        mvc.perform(get("/api/v1/claims/{n}", claimNumber).header(HttpHeaders.AUTHORIZATION, "Bearer " + submitter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[0].changedBy").value("submitter")); // audit records the real user

        mvc.perform(post("/api/v1/claims/{n}/adjudicate", claimNumber)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + submitter))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void adjudicatorCanAdjudicateAndTheCorrelationIdFlowsToFraudService() throws Exception {
        String adjudicator = login("adjudicator", "adjudicator-pass");
        String claimNumber = submitClaim(adjudicator);

        mvc.perform(post("/api/v1/claims/{n}/adjudicate", claimNumber)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adjudicator)
                        .header("X-Correlation-Id", "e2e-trace-12345678"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "e2e-trace-12345678"))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        fraudServiceStub.verify(postRequestedFor(urlEqualTo("/v1/score"))
                .withHeader("X-Correlation-Id", equalTo("e2e-trace-12345678"))
                .withHeader("X-API-Key", equalTo("local-dev-fraud-key")));
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());

        String docs = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(docs, "$.components.securitySchemes.bearerAuth.scheme")).isEqualTo("bearer");
        assertThat(JsonPath.<Object>read(docs, "$.paths['/api/v1/claims/{claimNumber}/adjudicate'].post")).isNotNull();
    }

    @Test
    void generatedCorrelationIdIsReturnedWhenNoneIsSent() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern("[0-9a-f-]{36}")));
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String submitClaim(String token) throws Exception {
        LocalDate date = LocalDate.now().minusDays(200 + (long) (Math.random() * 60));
        String body = """
                { "memberId": "AET100000003", "providerNpi": "1456789019", "serviceDate": "%s",
                  "lines": [ { "procedureCode": "99214", "diagnosisCode": "I10", "units": 1, "billedAmount": 150.00 } ] }
                """.formatted(date);
        String response = mvc.perform(post("/api/v1/claims").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.claimNumber");
    }
}

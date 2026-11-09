package com.example.claims.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.claims.TestcontainersConfiguration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full stack against a real PostgreSQL (Testcontainers), with the Flyway schema and seed data.
 * Each test uses its own seeded member so tests stay independent without truncating tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "adjudicator", roles = "ADJUDICATOR")
@Import(TestcontainersConfiguration.class)
class ClaimApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void submittedClaimCanBeFetchedWithLinesAndTotals() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("AET100000001", "1234567893", LocalDate.now().minusDays(3),
                                """
                                { "procedureCode": "99213", "diagnosisCode": "J45.909", "units": 1, "billedAmount": 125.50 },
                                { "procedureCode": "94640", "diagnosisCode": "J45.909", "units": 2, "billedAmount": 40.00 }
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimNumber").value(matchesPattern("CLM-\\d{4}-\\d{8}")))
                .andReturn();

        String location = created.getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.totalBilled").value(165.50))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[1].procedureCode").value("94640"));
    }

    @Test
    void submitForUnknownMemberIsRejectedWith422() throws Exception {
        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("AET999999999", "1234567893", LocalDate.now(), oneLine())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void claimFromUnknownProviderIsAcceptedForLaterAdjudication() throws Exception {
        // 1111111112 is Luhn-valid but not a seeded provider: intake accepts it; adjudication (phase 3) denies it.
        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("AET100000003", "1111111112", LocalDate.now(), oneLine())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    void searchFiltersByMemberAndServiceDateRangeAndPaginates() throws Exception {
        String member = "AET100000005";
        LocalDate base = LocalDate.now().minusDays(30);
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON)
                            .content(claimJson(member, "1987654328", base.plusDays(i * 10), oneLine())))
                    .andExpect(status().isCreated());
        }

        mvc.perform(get("/api/v1/claims").param("memberId", member).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].lines").doesNotExist());

        mvc.perform(get("/api/v1/claims").param("memberId", member)
                        .param("from", base.plusDays(5).toString())
                        .param("to", base.plusDays(15).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].serviceDate").value(base.plusDays(10).toString()));
    }

    @Test
    void sortingByUnknownPropertyIsAClientError() throws Exception {
        mvc.perform(get("/api/v1/claims").param("sort", "notAField"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void seededReferenceDataIsQueryable() throws Exception {
        mvc.perform(get("/api/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));

        mvc.perform(get("/api/v1/providers/1678901236"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inNetwork").value(false));

        mvc.perform(get("/api/v1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].memberId").value("AET100000001"))
                .andExpect(jsonPath("$[0].dateOfBirth").doesNotExist());

        mvc.perform(get("/api/v1/members/AET100000004"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverageEnd").value("2024-12-31"));
    }

    private static String oneLine() {
        return """
                { "procedureCode": "99213", "diagnosisCode": "M54.5", "units": 1, "billedAmount": 95.00 }
                """;
    }

    private static String claimJson(String memberId, String npi, LocalDate serviceDate, String lines) {
        return """
                { "memberId": "%s", "providerNpi": "%s", "serviceDate": "%s", "lines": [ %s ] }
                """.formatted(memberId, npi, serviceDate, lines);
    }
}

package com.example.claims.api;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.claims.api.dto.SubmitClaimRequest;
import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimLine;
import com.example.claims.service.BusinessRuleViolationException;
import com.example.claims.service.ClaimService;
import com.example.claims.service.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-layer slice: request binding, validation and error shapes. The service is mocked. */
@WebMvcTest(ClaimController.class)
class ClaimControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ClaimService claimService;

    private static final String VALID_BODY = """
            {
              "memberId": "AET100000001",
              "providerNpi": "1234567893",
              "serviceDate": "2026-11-01",
              "lines": [
                { "procedureCode": "99213", "diagnosisCode": "J45.909", "units": 1, "billedAmount": 125.50 }
              ]
            }
            """;

    @Test
    void submitReturns201WithLocationHeader() throws Exception {
        Claim claim = new Claim("CLM-2026-00000001", "AET100000001", "1234567893",
                LocalDate.of(2026, 11, 1), Instant.parse("2026-11-08T10:00:00Z"));
        claim.addLine(new ClaimLine("99213", "J45.909", 1, new BigDecimal("125.50")));
        when(claimService.submit(any(SubmitClaimRequest.class))).thenReturn(claim);

        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/claims/CLM-2026-00000001")))
                .andExpect(jsonPath("$.claimNumber").value("CLM-2026-00000001"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.totalBilled").value(125.50))
                .andExpect(jsonPath("$.lines[0].lineNumber").value(1));
    }

    @Test
    void submitWithInvalidFieldsReturnsProblemDetailListingEachField() throws Exception {
        String body = """
                {
                  "memberId": "BAD",
                  "providerNpi": "1234567890",
                  "serviceDate": "2999-01-01",
                  "lines": [
                    { "procedureCode": "ABC", "diagnosisCode": "123", "units": 0, "billedAmount": -5 }
                  ]
                }
                """;

        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.type").value(endsWith("/validation-error")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("memberId")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("providerNpi")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("serviceDate")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("lines[0].procedureCode")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("lines[0].diagnosisCode")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("lines[0].units")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("lines[0].billedAmount")));
    }

    @Test
    void submitWithNoLinesIsRejected() throws Exception {
        String body = """
                { "memberId": "AET100000001", "providerNpi": "1234567893", "serviceDate": "2026-11-01", "lines": [] }
                """;

        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines"));
    }

    @Test
    void malformedJsonReturns400ProblemDetail() throws Exception {
        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownMemberReturns422WithCode() throws Exception {
        when(claimService.submit(any())).thenThrow(
                new BusinessRuleViolationException("MEMBER_NOT_FOUND", "Member 'AET100000001' does not exist"));

        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void getUnknownClaimReturns404ProblemDetail() throws Exception {
        when(claimService.get("CLM-NOPE")).thenThrow(new ResourceNotFoundException("Claim", "CLM-NOPE"));

        mvc.perform(get("/api/v1/claims/CLM-NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Claim 'CLM-NOPE' was not found"));
    }

    @Test
    void invalidStatusFilterReturns400() throws Exception {
        mvc.perform(get("/api/v1/claims").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}

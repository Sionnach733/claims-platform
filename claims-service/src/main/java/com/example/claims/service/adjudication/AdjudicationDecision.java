package com.example.claims.service.adjudication;

import java.math.BigDecimal;
import java.util.List;

/** The engine's overall verdict for a claim. */
public sealed interface AdjudicationDecision {

    record Approved(BigDecimal allowedAmount, String rationale) implements AdjudicationDecision {
    }

    record Denied(String code, String reason, String rule) implements AdjudicationDecision {
    }

    record NeedsReview(List<String> reasons) implements AdjudicationDecision {
    }
}

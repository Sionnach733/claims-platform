package com.example.claims.events;

import com.example.claims.domain.ClaimStatus;
import java.time.Instant;
import java.util.UUID;

/** An unpublished row from the claim_status_history outbox. */
record OutboxRecord(long id, UUID eventId, String claimNumber, ClaimStatus fromStatus, ClaimStatus toStatus,
                    String reason, String changedBy, Instant changedAt) {

    ClaimStatusChangedEvent toEvent() {
        return new ClaimStatusChangedEvent(eventId, ClaimStatusChangedEvent.TYPE,
                ClaimStatusChangedEvent.SCHEMA_VERSION, claimNumber, fromStatus, toStatus, reason, changedBy,
                changedAt);
    }
}

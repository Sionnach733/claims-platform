package com.example.claims.events;

import com.example.claims.domain.ClaimStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Integration event published to SQS on every claim status change.
 *
 * <p>Deliberately minimal (data minimisation): it carries the claim number but no member
 * identifiers, names or diagnosis codes. Consumers that need more call the claims API
 * with their own authorisation. Delivery is at-least-once, so consumers de-duplicate on
 * {@code eventId}.
 */
public record ClaimStatusChangedEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String claimNumber,
        ClaimStatus fromStatus,
        ClaimStatus toStatus,
        String reason,
        String changedBy,
        Instant occurredAt) {

    public static final String TYPE = "ClaimStatusChanged";
    public static final int SCHEMA_VERSION = 1;
}

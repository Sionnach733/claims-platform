package com.example.claims.events;

import com.example.claims.domain.ClaimStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL access to the outbox. Plain JDBC rather than JPA: the access pattern is a locking batch read
 * plus a bulk update, and explicit SQL makes the locking semantics obvious.
 */
@Repository
class OutboxStore {

    private final JdbcClient jdbc;

    OutboxStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Locks up to {@code limit} unpublished rows. Must run inside a transaction. SKIP LOCKED means
     * concurrent pollers on other instances take different rows instead of waiting.
     */
    List<OutboxRecord> lockPending(int limit) {
        return jdbc.sql("""
                        SELECT h.id, h.event_id, c.claim_number, h.from_status, h.to_status,
                               h.reason, h.changed_by, h.changed_at
                        FROM claim_status_history h
                        JOIN claims c ON c.id = h.claim_id
                        WHERE h.published_at IS NULL
                        ORDER BY h.id
                        LIMIT :limit
                        FOR UPDATE OF h SKIP LOCKED
                        """)
                .param("limit", limit)
                .query((rs, n) -> new OutboxRecord(
                        rs.getLong("id"),
                        rs.getObject("event_id", UUID.class),
                        rs.getString("claim_number"),
                        rs.getString("from_status") == null ? null : ClaimStatus.valueOf(rs.getString("from_status")),
                        ClaimStatus.valueOf(rs.getString("to_status")),
                        rs.getString("reason"),
                        rs.getString("changed_by"),
                        rs.getObject("changed_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    void markPublished(List<Long> ids, Instant at) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.sql("UPDATE claim_status_history SET published_at = :at WHERE id IN (:ids)")
                .param("at", at.atOffset(ZoneOffset.UTC))
                .param("ids", ids)
                .update();
    }
}

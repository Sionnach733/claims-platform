-- Audit trail of every claim status change. Doubles as a transactional outbox:
-- rows are written in the same transaction as the status change, and a publisher
-- relays rows with published_at IS NULL to SQS (at-least-once delivery).

CREATE TABLE claim_status_history (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id      UUID         NOT NULL UNIQUE,      -- consumers de-duplicate on this
    claim_id      BIGINT       NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    from_status   VARCHAR(20),                       -- NULL for the initial SUBMITTED entry
    to_status     VARCHAR(20)  NOT NULL,
    reason        VARCHAR(500),
    changed_by    VARCHAR(100) NOT NULL,
    changed_at    TIMESTAMPTZ  NOT NULL,
    published_at  TIMESTAMPTZ
);

CREATE INDEX idx_status_history_claim ON claim_status_history (claim_id, changed_at);

-- Partial index: the outbox poller only ever scans the (small) unpublished set.
CREATE INDEX idx_status_history_unpublished ON claim_status_history (id) WHERE published_at IS NULL;

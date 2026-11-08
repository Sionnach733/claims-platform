-- Core claims schema. Targets PostgreSQL 16 / Aurora PostgreSQL.
-- Money is NUMERIC(12,2): never floating point for currency.

CREATE TABLE members (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id       VARCHAR(20)  NOT NULL UNIQUE,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    date_of_birth   DATE         NOT NULL,
    plan_code       VARCHAR(20)  NOT NULL,
    coverage_start  DATE         NOT NULL,
    coverage_end    DATE,                       -- NULL = open-ended coverage
    CONSTRAINT chk_coverage_range CHECK (coverage_end IS NULL OR coverage_end >= coverage_start)
);

CREATE TABLE providers (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    npi         VARCHAR(10)  NOT NULL UNIQUE CHECK (npi ~ '^[0-9]{10}$'),
    name        VARCHAR(200) NOT NULL,
    specialty   VARCHAR(100) NOT NULL,
    in_network  BOOLEAN      NOT NULL
);

CREATE SEQUENCE claim_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE claims (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_number    VARCHAR(30)    NOT NULL UNIQUE,
    member_id       VARCHAR(20)    NOT NULL REFERENCES members (member_id),
    -- No FK on provider: claims from unknown providers are accepted and then denied at adjudication.
    provider_npi    VARCHAR(10)    NOT NULL CHECK (provider_npi ~ '^[0-9]{10}$'),
    service_date    DATE           NOT NULL,
    submitted_at    TIMESTAMPTZ    NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    total_billed    NUMERIC(12, 2) NOT NULL,
    total_allowed   NUMERIC(12, 2),
    total_paid      NUMERIC(12, 2),
    denial_reason   VARCHAR(255),
    risk_score      NUMERIC(5, 2),
    version         BIGINT         NOT NULL DEFAULT 0,   -- optimistic locking
    CONSTRAINT chk_claim_status CHECK (status IN ('SUBMITTED', 'IN_REVIEW', 'APPROVED', 'DENIED', 'PAID'))
);

CREATE TABLE claim_lines (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id        BIGINT         NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    line_number     INT            NOT NULL,
    procedure_code  VARCHAR(5)     NOT NULL,   -- CPT
    diagnosis_code  VARCHAR(8)     NOT NULL,   -- ICD-10-CM
    units           INT            NOT NULL CHECK (units > 0),
    billed_amount   NUMERIC(12, 2) NOT NULL CHECK (billed_amount > 0),
    CONSTRAINT uq_claim_line UNIQUE (claim_id, line_number)
);

-- Indexes for the search API and the duplicate-claim rule (phase 3).
CREATE INDEX idx_claims_member_service_date ON claims (member_id, service_date);
CREATE INDEX idx_claims_status              ON claims (status);
CREATE INDEX idx_claims_service_date        ON claims (service_date);
CREATE INDEX idx_claims_provider            ON claims (provider_npi);
CREATE INDEX idx_claim_lines_claim          ON claim_lines (claim_id);

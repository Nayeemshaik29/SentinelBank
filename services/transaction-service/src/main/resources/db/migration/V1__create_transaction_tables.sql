-- One transfer per idempotency key. A caller retrying the exact same request (network timeout, a
-- double-clicked button) always lands on this same row instead of moving money twice.
CREATE TABLE transfers (
    id               UUID          PRIMARY KEY,
    idempotency_key  VARCHAR(128)  NOT NULL,
    request_hash     VARCHAR(64)   NOT NULL,
    owner_id         UUID          NOT NULL,
    from_account_id  UUID          NOT NULL,
    -- Opaque destination reference (a partner-bank account, from Day 6). Not one of our own account ids,
    -- so it is not validated against account-service here.
    to_account_id    VARCHAR(64)   NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    amount_minor     BIGINT        NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    failure_code     VARCHAR(64),
    failure_detail   VARCHAR(500),
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_transfers_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_transfers_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_transfers_status
        CHECK (status IN ('PENDING', 'DEBITED', 'FAILED', 'COMPLETED', 'COMPENSATING', 'REVERSED'))
);

CREATE INDEX ix_transfers_owner ON transfers (owner_id, created_at);

-- Transactional outbox: a row here is written in the SAME database transaction as the transfer moving to
-- DEBITED, so the two can never disagree (no event without a state change, no state change without an
-- event). The Day 5 publisher polls published_at IS NULL and marks rows once sent to Kafka.
CREATE TABLE outbox_events (
    id              UUID          PRIMARY KEY,
    aggregate_id    UUID          NOT NULL,
    event_type      VARCHAR(64)   NOT NULL,
    payload         TEXT          NOT NULL,
    correlation_id  VARCHAR(64),
    created_at      TIMESTAMPTZ   NOT NULL,
    published_at    TIMESTAMPTZ
);

CREATE INDEX ix_outbox_events_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

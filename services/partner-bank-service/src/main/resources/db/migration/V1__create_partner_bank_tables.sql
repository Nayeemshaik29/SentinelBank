-- One row per transfer this mock partner bank was asked to credit. transfer_id is unique: consuming the
-- same transfer.initiated event twice (Kafka's at-least-once delivery) must never credit twice.
CREATE TABLE partner_credits (
    id             UUID         PRIMARY KEY,
    transfer_id    UUID         NOT NULL,
    from_account_id UUID        NOT NULL,
    to_account_id  VARCHAR(64)  NOT NULL,
    amount_minor   BIGINT       NOT NULL,
    currency       VARCHAR(3)   NOT NULL,
    outcome        VARCHAR(10)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_partner_credits_transfer_id UNIQUE (transfer_id),
    CONSTRAINT ck_partner_credits_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_partner_credits_outcome CHECK (outcome IN ('CREDITED', 'REJECTED'))
);

-- The same transactional outbox pattern as transaction-service: transfer.completed / transfer.failed is
-- written here in the same transaction as the partner_credits row, and a separate poller publishes it.
CREATE TABLE outbox_events (
    id              UUID          PRIMARY KEY,
    aggregate_id    UUID          NOT NULL,
    event_type      VARCHAR(64)   NOT NULL,
    payload         TEXT          NOT NULL,
    partition_key   VARCHAR(64)   NOT NULL,
    correlation_id  VARCHAR(64),
    created_at      TIMESTAMPTZ   NOT NULL,
    published_at    TIMESTAMPTZ
);

CREATE INDEX ix_outbox_events_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

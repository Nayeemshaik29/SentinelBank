-- Balances are money out of a general ledger, in the smallest currency unit (cents), never a float.
CREATE TABLE accounts (
    id              UUID          PRIMARY KEY,
    owner_id        UUID          NOT NULL,
    account_number  VARCHAR(20)   NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    balance_minor   BIGINT        NOT NULL DEFAULT 0,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_accounts_number UNIQUE (account_number),
    CONSTRAINT ck_accounts_balance_non_negative CHECK (balance_minor >= 0),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE INDEX ix_accounts_owner ON accounts (owner_id);

-- Append-only. The balance on `accounts` is a cached total; this table is the source of truth for how it
-- got there. One row per debit/credit, never updated or deleted.
CREATE TABLE ledger_entries (
    id              UUID          PRIMARY KEY,
    account_id      UUID          NOT NULL REFERENCES accounts (id),
    entry_type      VARCHAR(10)   NOT NULL,
    amount_minor    BIGINT        NOT NULL,
    balance_after   BIGINT        NOT NULL,
    -- The business operation this entry belongs to (e.g. a transfer id). Idempotency key: the same
    -- (account_id, reference_id, entry_type) can only be recorded once.
    reference_id    VARCHAR(64)   NOT NULL,
    description     VARCHAR(200),
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_ledger_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT uq_ledger_idempotency UNIQUE (account_id, reference_id, entry_type)
);

CREATE INDEX ix_ledger_entries_account ON ledger_entries (account_id, created_at);

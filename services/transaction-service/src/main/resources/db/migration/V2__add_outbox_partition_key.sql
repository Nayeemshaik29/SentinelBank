-- The Kafka message key (README: "message key is accountId"), decided by the writer at the same time as
-- the event itself, so the publisher never needs to know how to derive a key from an arbitrary payload.
ALTER TABLE outbox_events ADD COLUMN partition_key VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE outbox_events ALTER COLUMN partition_key DROP DEFAULT;

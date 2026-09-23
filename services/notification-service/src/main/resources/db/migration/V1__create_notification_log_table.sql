-- One row per transfer this service has notified the customer about. transfer_id is unique: consuming the
-- same transfer.completed / transfer.failed event twice (Kafka's at-least-once delivery) must never send
-- (or record) a second notification for the same outcome.
CREATE TABLE notification_log (
    id               UUID         PRIMARY KEY,
    transfer_id      UUID         NOT NULL,
    recipient_email  VARCHAR(255) NOT NULL,
    type             VARCHAR(30)  NOT NULL,
    sent_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_notification_log_transfer_id UNIQUE (transfer_id),
    CONSTRAINT ck_notification_log_type CHECK (type IN ('TRANSFER_COMPLETED', 'TRANSFER_FAILED'))
);

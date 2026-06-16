ALTER TABLE payment_records
    ADD COLUMN provider VARCHAR(40) NOT NULL DEFAULT 'TICKETFORGE_SIMULATOR',
    ADD COLUMN provider_event_id VARCHAR(120),
    ADD COLUMN amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    ADD COLUMN processed_at TIMESTAMPTZ,
    ADD COLUMN failure_reason VARCHAR(255);

UPDATE payment_records pr
SET amount = o.total_amount
FROM ticket_orders o
WHERE pr.order_id = o.id;

ALTER TABLE payment_records
    ADD CONSTRAINT ck_payment_records_amount_non_negative CHECK (amount >= 0),
    ADD CONSTRAINT ck_payment_records_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_payment_records_processed_not_before_created CHECK (processed_at IS NULL OR processed_at >= created_at);

CREATE UNIQUE INDEX uk_payment_records_provider_event_id
ON payment_records (provider_event_id)
WHERE provider_event_id IS NOT NULL;

CREATE INDEX idx_payment_records_order_status
ON payment_records (order_id, status);

CREATE UNIQUE INDEX uk_payment_records_one_pending_per_order
ON payment_records (order_id)
WHERE status = 'PENDING';

ALTER TABLE ticket_orders
    ADD COLUMN cancelled_at TIMESTAMPTZ;

UPDATE ticket_orders
SET idempotency_key = 'legacy-' || order_number
WHERE idempotency_key IS NULL;

ALTER TABLE ticket_orders
    ALTER COLUMN idempotency_key SET NOT NULL;

ALTER TABLE ticket_orders
    ADD CONSTRAINT ck_ticket_orders_idempotency_key_not_blank
        CHECK (btrim(idempotency_key) <> '');

ALTER TABLE ticket_orders
    ADD CONSTRAINT ck_ticket_orders_pending_requires_expires_at
        CHECK (status <> 'PENDING_PAYMENT' OR expires_at IS NOT NULL);

ALTER TABLE ticket_orders
    ADD CONSTRAINT ck_ticket_orders_cancelled_requires_cancelled_at
        CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL);

ALTER TABLE ticket_orders
    ADD CONSTRAINT ck_ticket_orders_paid_requires_paid_at
        CHECK (status <> 'PAID' OR paid_at IS NOT NULL);

ALTER TABLE ticket_orders
    ADD CONSTRAINT ck_ticket_orders_expires_not_before_created
        CHECK (expires_at IS NULL OR expires_at >= created_at);

ALTER TABLE ticket_orders
    ADD CONSTRAINT ck_ticket_orders_cancelled_not_before_created
        CHECK (cancelled_at IS NULL OR cancelled_at >= created_at);

ALTER TABLE ticket_orders
    ADD CONSTRAINT ck_ticket_orders_paid_not_before_created
        CHECK (paid_at IS NULL OR paid_at >= created_at);

CREATE INDEX idx_ticket_orders_pending_expiry
ON ticket_orders (expires_at)
WHERE status = 'PENDING_PAYMENT';

UPDATE events
SET sales_start_at = '2025-01-01T00:00:00Z',
    updated_at = NOW()
WHERE slug = 'ticketforge-opening-live';

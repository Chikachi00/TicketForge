CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(160) NOT NULL,
    name VARCHAR(255) NOT NULL,
    venue VARCHAR(255) NOT NULL,
    description TEXT,
    performance_at TIMESTAMPTZ NOT NULL,
    sales_start_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_events_slug UNIQUE (slug),
    CONSTRAINT ck_events_status CHECK (status IN ('DRAFT', 'ON_SALE', 'SOLD_OUT', 'CANCELLED'))
);

CREATE INDEX idx_events_status_sales_start_at ON events (status, sales_start_at);
CREATE INDEX idx_events_performance_at ON events (performance_at);

CREATE TABLE ticket_tiers (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    price NUMERIC(12,2) NOT NULL,
    total_stock INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ticket_tiers_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,
    CONSTRAINT uk_ticket_tiers_event_code UNIQUE (event_id, code),
    CONSTRAINT ck_ticket_tiers_price_non_negative CHECK (price >= 0),
    CONSTRAINT ck_ticket_tiers_total_stock_non_negative CHECK (total_stock >= 0)
);

CREATE INDEX idx_ticket_tiers_event_id ON ticket_tiers (event_id);

CREATE TABLE ticket_inventory (
    id BIGSERIAL PRIMARY KEY,
    ticket_tier_id BIGINT NOT NULL,
    available_stock INTEGER NOT NULL DEFAULT 0,
    reserved_stock INTEGER NOT NULL DEFAULT 0,
    sold_stock INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ticket_inventory_ticket_tier FOREIGN KEY (ticket_tier_id) REFERENCES ticket_tiers (id) ON DELETE CASCADE,
    CONSTRAINT uk_ticket_inventory_ticket_tier UNIQUE (ticket_tier_id),
    CONSTRAINT ck_ticket_inventory_available_non_negative CHECK (available_stock >= 0),
    CONSTRAINT ck_ticket_inventory_reserved_non_negative CHECK (reserved_stock >= 0),
    CONSTRAINT ck_ticket_inventory_sold_non_negative CHECK (sold_stock >= 0),
    CONSTRAINT ck_ticket_inventory_version_non_negative CHECK (version >= 0)
);

CREATE TABLE ticket_orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(80) NOT NULL,
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    ticket_tier_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(120),
    expires_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ticket_orders_order_number UNIQUE (order_number),
    CONSTRAINT uk_ticket_orders_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_ticket_orders_user FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT fk_ticket_orders_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_ticket_orders_ticket_tier FOREIGN KEY (ticket_tier_id) REFERENCES ticket_tiers (id),
    CONSTRAINT ck_ticket_orders_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_ticket_orders_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT ck_ticket_orders_total_amount_non_negative CHECK (total_amount >= 0),
    CONSTRAINT ck_ticket_orders_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED', 'REFUNDED'))
);

CREATE INDEX idx_ticket_orders_user_status ON ticket_orders (user_id, status);
CREATE INDEX idx_ticket_orders_event_status ON ticket_orders (event_id, status);
CREATE INDEX idx_ticket_orders_status_expires_at ON ticket_orders (status, expires_at);

CREATE TABLE payment_records (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    payment_transaction_id VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    callback_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_payment_records_order FOREIGN KEY (order_id) REFERENCES ticket_orders (id),
    CONSTRAINT uk_payment_records_transaction UNIQUE (payment_transaction_id),
    CONSTRAINT ck_payment_records_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_payment_records_order_id ON payment_records (order_id);
CREATE INDEX idx_payment_records_status ON payment_records (status);


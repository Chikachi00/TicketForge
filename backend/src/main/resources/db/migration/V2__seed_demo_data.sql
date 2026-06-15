INSERT INTO app_users (email, display_name, role)
VALUES
    ('admin@ticketforge.local', 'TicketForge Admin', 'ADMIN'),
    ('user@ticketforge.local', 'TicketForge Demo User', 'USER');

INSERT INTO events (slug, name, venue, description, performance_at, sales_start_at, status)
VALUES (
    'ticketforge-opening-live',
    'TicketForge Opening Live',
    'Yokohama Arena',
    'A demo event used to verify the first TicketForge read path.',
    '2026-09-20T10:00:00Z',
    '2026-07-01T01:00:00Z',
    'ON_SALE'
);

INSERT INTO ticket_tiers (event_id, code, name, price, total_stock)
SELECT id, 'VIP', 'VIP', 1280.00, 100
FROM events
WHERE slug = 'ticketforge-opening-live';

INSERT INTO ticket_tiers (event_id, code, name, price, total_stock)
SELECT id, 'S', 'S', 880.00, 500
FROM events
WHERE slug = 'ticketforge-opening-live';

INSERT INTO ticket_tiers (event_id, code, name, price, total_stock)
SELECT id, 'A', 'A', 580.00, 1000
FROM events
WHERE slug = 'ticketforge-opening-live';

INSERT INTO ticket_inventory (ticket_tier_id, available_stock, reserved_stock, sold_stock, version)
SELECT id, total_stock, 0, 0, 0
FROM ticket_tiers;


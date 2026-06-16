# TicketForge Architecture

TicketForge is a modular monolith. Code is organized by business area, while the backend remains one Spring Boot application. This keeps local development simple and lets the project focus on transaction correctness before introducing distributed systems.

## Runtime Shape

- PostgreSQL is the source of truth for events, ticket tiers, orders, inventory, and payment records.
- Flyway owns schema changes. Hibernate validates the schema and never creates or mutates tables.
- Redis is reserved for later queueing, temporary reservation, and idempotency experiments. Phase 3 does not use Redis for business logic.
- React is only the user interface. It calls backend APIs and does not hard-code event data.
- The simulated payment provider is local development tooling, not a real payment integration.

## Current Data Flow

1. Spring Boot starts and Flyway applies `V1` through `V4`.
2. The frontend loads `/api/events` and `/api/events/{eventId}`.
3. The user reserves a ticket tier with `POST /api/orders`.
4. The backend validates the event and tier, then reserves inventory with a PostgreSQL conditional update.
5. The user creates a payment session with `POST /api/payments/orders/{orderNumber}`.
6. The local simulator sends a signed callback to the same callback service used by external callbacks.
7. A successful callback locks payment first, then order, then converts `reserved_stock` to `sold_stock`.
8. A failed callback marks the payment failed and leaves the order pending.
9. Cancellation and expiration compete with payment by locking the same order row. Only one final state wins.

## Why Not Microservices Yet

The project is intentionally a modular monolith. The hard problem in this phase is transaction correctness: order state transitions, idempotent callbacks, row locks, and inventory invariants. Splitting services now would add network and distributed consistency complexity before the local transaction model is proven.

## Inventory Invariant

Every reservation, release, and payment success must preserve:

```text
available_stock + reserved_stock + sold_stock = total_stock
```

The backend uses PostgreSQL conditional updates rather than Java locks, Redis locks, or client-side stock calculations.

## Payment Consistency

Payment callbacks are signed with HMAC-SHA256. The callback service validates signature, status, amount, currency, and order identity before changing state.

Duplicate success callbacks are idempotent. If payment wins a race against cancellation or expiration, the order becomes `PAID` and stock becomes sold. If cancellation or expiration wins first, the order remains `CANCELLED`, stock is released, and a later success callback is rejected with `ORDER_ALREADY_CANCELLED`.

No concurrency benchmark or capacity number is claimed in this phase.

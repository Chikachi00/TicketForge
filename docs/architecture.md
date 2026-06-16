# TicketForge Architecture

TicketForge is a modular monolith. The code is organized by business area, but the deployable backend is a single Spring Boot application. This keeps local development simple while preserving clear boundaries for later concurrency experiments.

## Runtime Shape

- PostgreSQL is the source of truth for business data and the concurrency-control layer for Phase 2 inventory reservation.
- Flyway owns database versioning. Hibernate validates the schema but does not create or modify tables.
- Redis is reserved for later queueing and temporary reservation experiments. Phase 2 does not require Redis and does not use Redis locks or Lua.
- React is responsible only for the user interface and reads data from the backend API.
- Docker Compose remains available as optional local infrastructure, but Phase 2 targets Windows local PostgreSQL.

## Current Data Flow

1. Spring Boot starts and Flyway applies `V1`, `V2`, and `V3`.
2. The frontend calls `/api/events` and `/api/events/{eventId}` through the Vite development proxy.
3. The user selects a ticket tier and quantity.
4. The frontend sends `POST /api/orders` with `X-User-Email` and `Idempotency-Key`.
5. The backend locks the demo user row, checks idempotency, validates the event and tier, and reserves inventory with one PostgreSQL conditional `UPDATE`.
6. The backend creates a `PENDING_PAYMENT` order with a 5-minute expiration.
7. Manual cancellation or scheduled expiration releases reserved stock back to available stock.

## Why Not Microservices Yet

TicketForge will eventually explore high-concurrency ticketing concerns, but Phase 1 is deliberately small. Splitting services now would add networking, deployment, and data-consistency complexity before there is enough domain behavior to justify it.

## Inventory Invariant

Phase 2 relies on PostgreSQL conditional updates rather than Java locks, Redis locks, or client-side stock calculations. Every reservation and release must preserve:

```text
available_stock + reserved_stock + sold_stock = total_stock
```

No concurrency benchmark or capacity number is claimed in this phase.

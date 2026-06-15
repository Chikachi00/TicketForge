# TicketForge Architecture

TicketForge Phase 1 is a modular monolith. The code is organized by business area, but the deployable backend is a single Spring Boot application. This keeps the first phase easy to run locally while leaving clear boundaries for later concurrency experiments.

## Runtime Shape

- PostgreSQL is the source of truth for business data.
- Flyway owns database versioning. Hibernate validates the schema but does not create or modify tables.
- Redis is started by Docker Compose but is reserved for later queueing, temporary reservation, and idempotency-control experiments.
- React is responsible only for the user interface and reads data from the backend API.
- Adminer is included for local database inspection.

## Current Data Flow

1. Docker Compose starts PostgreSQL, Redis, and Adminer.
2. Spring Boot starts and Flyway applies `V1__create_core_schema.sql` and `V2__seed_demo_data.sql`.
3. The backend reads events, ticket tiers, and inventory from PostgreSQL.
4. The frontend calls `/api/events` and `/api/events/{eventId}` through the Vite development proxy.
5. The page renders event and ticket-tier data returned by the backend.

## Why Not Microservices Yet

TicketForge will eventually explore high-concurrency ticketing concerns, but Phase 1 is deliberately small. Splitting services now would add networking, deployment, and data-consistency complexity before there is enough domain behavior to justify it.

No concurrency benchmark or capacity number is claimed in this phase.


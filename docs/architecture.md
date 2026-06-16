# TicketForge Architecture

TicketForge remains a modular monolith. The backend is one Spring Boot application, organized by business area. Portfolio v1 focuses on demonstrating the existing PostgreSQL transaction model before adding Redis, queues, or distributed deployment.

## Runtime Shape

- PostgreSQL is the source of truth for events, ticket tiers, users, orders, inventory, and payment records.
- Flyway owns schema changes. Hibernate only validates the schema.
- Redis remains reserved for future queueing and temporary reservation experiments.
- React remains the UI only.
- k6 drives load through public HTTP APIs plus loadtest-only management APIs.

## Profiles

- Default profile: normal local development using `ticketforge`.
- `integration` Maven profile: PostgreSQL integration tests using `ticketforge_test`.
- Spring `loadtest` profile: dedicated performance/correctness runs using `ticketforge_loadtest`.
- Spring `demo` profile: local portfolio demo tools using `ticketforge`.

The loadtest management controller is only registered in the `loadtest` profile. It is not available in default, dev, or prod.

The demo management controller is only registered in the `demo` profile and is explicitly excluded from `prod`.

## Phase 4 Data Flow

1. Start Spring Boot with `-Dspring-boot.run.profiles=loadtest`.
2. Flyway applies V1 through V4 to `ticketforge_loadtest`.
3. k6 or PowerShell calls `/api/load-test/reset` with `X-Load-Test-Secret`.
4. Reset creates the dedicated event, ticket tier, inventory row, and generated load-test users.
5. k6 runs one of the scenarios through the normal order/payment APIs.
6. k6 calls `/api/load-test/state` to verify final PostgreSQL state.
7. k6 writes summary JSON under `load-tests/results/`.

## Correctness Model

Every scenario ultimately verifies PostgreSQL state, not just HTTP responses.

```text
available_stock + reserved_stock + sold_stock = total_stock
```

Oversell is detected from database state and order counts. Negative inventory is detected from database stock columns.

`OUT_OF_STOCK` is expected once stock is exhausted in spike scenarios. It is counted as a normal business rejection only when the error code is exactly `OUT_OF_STOCK`.

## Observability

Micrometer keeps the default JVM, HTTP, HikariCP, and datasource metrics. TicketForge adds low-cardinality business metrics for:

- orders created
- order idempotent replay
- order rejection
- inventory reserved/released
- payment success/failure
- payment callback replay
- order reservation duration
- payment callback duration

Metric tags must stay bounded. IDs, emails, idempotency keys, order numbers, payment transaction ids, provider event ids, and user ids must never be used as tags.

## Baseline Interpretation

Phase 4 creates a PostgreSQL baseline for future comparison with Redis, queueing, or multi-instance designs. Local single-machine results are not production capacity numbers. If k6 and the backend run on the same machine, they compete for CPU and memory.

No performance result should be published unless the scenario was actually executed and the environment was recorded.

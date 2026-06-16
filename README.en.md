# TicketForge

[中文 README](README.md)

TicketForge is a high-concurrency ticketing-system lab inspired by platforms such as ePlus, Ticket Pia, and Damai.

The current stage is **Phase 2: PostgreSQL Inventory Reservation and Idempotent Orders**. Phase 2 targets Windows local PostgreSQL and does not require Docker, Redis, payment, virtual queueing, or message queues.

## Current Features

- Event and ticket-tier query APIs.
- Database versioning with PostgreSQL and Flyway.
- Pending order creation.
- Atomic PostgreSQL inventory reservation.
- Oversell and negative-stock prevention.
- Idempotent order submission with `Idempotency-Key`.
- Manual order cancellation with stock release.
- Automatic cancellation of unpaid orders after 5 minutes.
- React UI for reservation, order details, countdown, cancellation, and recent orders.

## Tech Stack

- Backend: Java 21, Spring Boot 3.5.15, Maven Wrapper, Spring Web, Spring Data JPA, Validation, Actuator, Flyway, PostgreSQL Driver, JUnit 5, Mockito
- Frontend: React, TypeScript, Vite, npm, CSS
- Database: Windows local PostgreSQL
- Optional infrastructure: `compose.yaml` remains in the repo, but Docker is not required in Phase 2

## Local Database

```text
Host: localhost
Port: 5432
Database: ticketforge
Username: ticketforge
Password: ticketforge_dev
```

Redis is not required. Redis health is disabled, so `/actuator/health` can remain `UP` without Redis.

## Start Backend

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Flyway files live in:

```text
backend/src/main/resources/db/migration/
```

- `V1__create_core_schema.sql`: core tables.
- `V2__seed_demo_data.sql`: demo users, event, ticket tiers, and inventory.
- `V3__prepare_order_reservation.sql`: cancellation timestamp, non-null idempotency key, pending-expiry index, order time constraints, and demo sales-start update.

## Start Frontend

```powershell
cd frontend
npm ci
npm run dev
```

Open:

- Frontend: http://localhost:5173
- Health: http://localhost:8080/actuator/health
- Events API: http://localhost:8080/api/events

## Demo Identity

There is no login system yet. The backend uses a request header as the current demo user:

```http
X-User-Email: user@ticketforge.local
```

The frontend labels this user as `Demo User`. Real authentication will replace this later.

## APIs

Events:

```http
GET /api/events
GET /api/events/{eventId}
GET /api/events/slug/{slug}
```

Orders:

```http
POST /api/orders
GET /api/orders/{orderNumber}
GET /api/orders/me
GET /api/orders/me?status=PENDING_PAYMENT
POST /api/orders/{orderNumber}/cancel
```

Create an order:

```powershell
$headers = @{
  "X-User-Email" = "user@ticketforge.local"
  "Idempotency-Key" = [guid]::NewGuid().ToString()
}

$body = @{
  ticketTierId = 1
  quantity = 1
} | ConvertTo-Json

$order = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/orders" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $body

$order
```

Submitting the same `$headers` and `$body` again returns the same order with `idempotentReplay = true`.

## Inventory Reservation

Order creation runs in one PostgreSQL transaction:

1. Lock the demo user row by `X-User-Email`.
2. Check whether `user_id + idempotency_key` already exists.
3. Validate ticket tier, event status, and sales start.
4. Reserve inventory with an atomic PostgreSQL conditional `UPDATE`.
5. Create the `PENDING_PAYMENT` order.

The stock gate:

```sql
UPDATE ticket_inventory
SET available_stock = available_stock - :quantity,
    reserved_stock = reserved_stock + :quantity,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE ticket_tier_id = :ticketTierId
  AND available_stock >= :quantity;
```

When the affected row count is `0`, the API returns `OUT_OF_STOCK`.

Inventory invariant:

```text
available_stock >= 0
reserved_stock >= 0
sold_stock >= 0
available_stock + reserved_stock + sold_stock = total_stock
```

## Idempotency

The same user submitting the same `Idempotency-Key` repeatedly creates only one order and reserves stock once. The application serializes repeated submissions for the same user with a `PESSIMISTIC_WRITE` user-row lock. The database unique constraint `UNIQUE(user_id, idempotency_key)` remains the final guard.

## Cancellation and Expiration

Only `PENDING_PAYMENT` orders can be actively cancelled. Cancellation locks the order row, sets `CANCELLED/cancelled_at`, and releases reserved stock back to available stock in the same transaction.

Pending orders expire after 5 minutes by default:

```yaml
ticketforge:
  orders:
    reservation-ttl: PT5M
    expiration-scan-delay-ms: 10000
```

The scheduler scans up to 100 expired pending orders per batch and cancels them one by one. Core business logic uses an injected `Clock`.

## Tests

Default backend tests do not require Docker, Redis, or an external database:

```powershell
cd backend
.\mvnw.cmd test
```

Real PostgreSQL integration tests:

```powershell
cd backend
.\mvnw.cmd verify -Pintegration
```

The integration profile uses `ticketforge_test` and includes concurrent oversell prevention plus concurrent idempotency tests. GitHub Actions starts PostgreSQL 17 and runs this profile.

Frontend:

```powershell
cd frontend
npm ci
npm run build
```

## Not Implemented Yet

- Registration, login, and JWT
- Real payment
- Redis business logic
- Virtual queue
- Message queues
- k6 load tests
- WebSocket/SSE
- Microservices and Kubernetes
- Seat selection

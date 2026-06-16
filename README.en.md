# TicketForge

[中文 README](README.md)

TicketForge is a high-concurrency ticketing-system lab inspired by ePlus, Ticket Pia, Damai, and similar platforms. The current stage is **Phase 3: Simulated Payment, Idempotent Callback and Order State Machine**.

Phase 3 uses React + TypeScript, Spring Boot Java 21, and Windows local PostgreSQL. It does not use Docker, Redis, message queues, real payment providers, JWT, virtual queueing, or microservices.

## Current Features

- Event and ticket-tier query APIs.
- PostgreSQL schema versioning with Flyway.
- Pending order creation with atomic inventory reservation.
- Idempotent order submission with `Idempotency-Key`.
- Manual cancellation and scheduled expiration.
- Simulated payment session creation.
- HMAC-SHA256 payment callback verification.
- Successful payment converts reserved stock to sold stock.
- Failed payment keeps the order pending and keeps stock reserved.
- Duplicate success callbacks are idempotent.
- React UI for reservation, cancellation, simulated payment success/failure, and order refresh.

## Stack

- Backend: Java 21, Spring Boot 3.5.15, Maven Wrapper, Spring Web, Spring Data JPA, Validation, Actuator, Flyway, PostgreSQL Driver, JUnit 5, Mockito
- Frontend: React, TypeScript, Vite, npm, CSS
- Database: Windows local PostgreSQL
- CI: GitHub Actions with PostgreSQL 17

## Local Database

```text
Host: localhost
Port: 5432
Database: ticketforge
Test database: ticketforge_test
Username: ticketforge
Password: ticketforge_dev
```

Proxy example:

```powershell
$env:HTTP_PROXY="http://127.0.0.1:7890"
$env:HTTPS_PROXY="http://127.0.0.1:7890"
git config --local http.proxy http://127.0.0.1:7890
git config --local https.proxy http://127.0.0.1:7890
```

## Run Backend

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Flyway migrations:

- `V1__create_core_schema.sql`: core tables.
- `V2__seed_demo_data.sql`: demo users, event, ticket tiers, and inventory.
- `V3__prepare_order_reservation.sql`: reservation, cancellation, and expiration support.
- `V4__prepare_simulated_payment.sql`: simulated payment fields, constraints, idempotency indexes, and one pending payment per order.

## Run Frontend

```powershell
cd frontend
npm ci
npm run dev
```

Open:

- Frontend: http://localhost:5173
- Health: http://localhost:8080/actuator/health
- Events API: http://localhost:8080/api/events

## APIs

```http
GET /api/events
GET /api/events/{eventId}
GET /api/events/slug/{slug}
POST /api/orders
GET /api/orders/{orderNumber}
GET /api/orders/me
POST /api/orders/{orderNumber}/cancel
POST /api/payments/orders/{orderNumber}
GET /api/payments/{paymentTransactionId}
POST /api/payments/callback
POST /api/payment-simulator/{paymentTransactionId}/success
POST /api/payment-simulator/{paymentTransactionId}/failure
```

The simulator endpoints are disabled in the `prod` profile.

## Payment Callback Signature

Configuration:

```yaml
ticketforge:
  payment:
    callback-secret: ${TICKETFORGE_PAYMENT_CALLBACK_SECRET:ticketforge-local-dev-secret}
```

Signing string:

```text
providerEventId|paymentTransactionId|orderNumber|status|amount|currency|occurredAt
```

Amounts use exactly two decimal places, for example `1280.00`. Times are UTC ISO 8601 values such as `2026-06-16T10:02:00Z`. The backend verifies HMAC-SHA256 with UTF-8 input and constant-time comparison.

## Payment State Machine

Allowed:

```text
PENDING_PAYMENT -> PAID
PENDING_PAYMENT -> CANCELLED
```

Reserved for later:

```text
PAID -> REFUNDED
```

Forbidden:

```text
CANCELLED -> PAID
PAID -> CANCELLED
CANCELLED -> PENDING_PAYMENT
```

Successful payment locks the payment row, locks the order row, atomically converts reserved stock to sold stock, marks the order `PAID`, and marks the payment `SUCCESS`. Failed payment marks only the payment as `FAILED`; the order remains `PENDING_PAYMENT`.

Duplicate success callbacks return `idempotentReplay=true` and do not transfer inventory again. Payment vs cancellation and payment vs expiration races allow only one final order state and one inventory transition.

## Tests

Default backend tests:

```powershell
cd backend
.\mvnw.cmd test
```

PostgreSQL integration tests:

```powershell
cd backend
.\mvnw.cmd verify -Pintegration
```

Frontend:

```powershell
cd frontend
npm ci
npm run build
```

## Not Implemented

- Registration, login, and JWT
- Real payment providers and refunds
- Redis business logic, Redis locks, Lua
- Virtual queueing
- Message queues
- k6 load tests
- WebSocket/SSE
- Microservices and Kubernetes
- Seat selection

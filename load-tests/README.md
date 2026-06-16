# TicketForge Load Tests

This directory contains native Windows k6 scripts for Phase 4. Docker is not used for load testing.

## Requirements

```powershell
k6 version
```

If k6 is missing:

```powershell
winget install k6 --source winget
```

The backend must run with the `loadtest` profile and point to the dedicated database `ticketforge_loadtest`, not the normal `ticketforge` database.

## Dedicated Database

Create the database once with a PostgreSQL administrator account:

```sql
CREATE DATABASE ticketforge_loadtest
    OWNER ticketforge
    ENCODING 'UTF8';
```

Do not grant `CREATE DATABASE` to the normal `ticketforge` user just for this workflow.

## Environment Variables

All scripts share these variables:

```text
BASE_URL=http://127.0.0.1:8080
LOAD_TEST_SECRET=ticketforge-local-loadtest-secret
PAYMENT_CALLBACK_SECRET=ticketforge-local-dev-secret
EVENT_SLUG=ticketforge-load-test-live
TICKET_CODE=LOAD
TOTAL_STOCK=100
USER_COUNT=1000
VUS=50
DURATION=30s
TARGET_RPS=50
MAX_VUS=100
```

## Scenarios

- `scenarios/smoke.js`: health, events, one order, payment success, final state.
- `scenarios/order-baseline.js`: steady order creation baseline without payment.
- `scenarios/oversell-spike.js`: many users compete for limited stock.
- `scenarios/idempotency-retry.js`: concurrent retry with the same user and same `Idempotency-Key`.
- `scenarios/payment-callback-replay.js`: repeated identical signed success callbacks.
- `scenarios/full-journey.js`: event query, order, payment session, payment success, final order query.

## Run

Start the backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=loadtest"
```

Run smoke:

```powershell
cd load-tests
.\scripts\run-smoke.ps1
```

Run correctness scenarios:

```powershell
.\scripts\run-correctness.ps1
```

Run baseline scenarios:

```powershell
.\scripts\run-baseline.ps1
```

Run all local checks:

```powershell
.\scripts\run-all.ps1
```

## Thresholds

Correctness thresholds are strict:

```text
oversell_detected count == 0
inventory_inconsistent count == 0
duplicate_order_detected count == 0
duplicate_payment_processing_detected count == 0
unexpected_error count == 0
```

Performance thresholds are provisional guardrails, not production SLOs:

```text
http_req_duration p(95) < 3000ms
http_req_duration p(99) < 5000ms
```

## Results

Raw summary JSON is written to `load-tests/results/`. Large raw logs, CSV files, and JSON summaries are ignored by Git. Keep only `.gitkeep` and small templates in the repository.

`OUT_OF_STOCK` is expected in oversell-spike once stock is exhausted. It is not a system error. Other 409 responses, 5xx responses, invalid JSON, negative inventory, oversell, and inventory invariant failures are test failures.

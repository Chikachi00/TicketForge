# TicketForge

[中文 README](README.md)

TicketForge is a high-concurrency ticketing-system lab inspired by ePlus, Ticket Pia, Damai, and similar platforms. The current stage is **Phase 4: k6 Load Testing, Observability and PostgreSQL Baseline**.

Phase 4 adds a dedicated `loadtest` Spring profile, native Windows k6 scenarios, low-cardinality Micrometer business metrics, and a reproducible PostgreSQL baseline workflow. It still does not add Redis, message queues, real payment providers, JWT, virtual queueing, WebSocket, microservices, Kubernetes, Grafana Server, or Prometheus Server.

## Databases

```text
Development: ticketforge
Integration: ticketforge_test
Load test:   ticketforge_loadtest
Username:    ticketforge
Password:    ticketforge_dev
```

Create the load-test database once with a PostgreSQL administrator account:

```sql
CREATE DATABASE ticketforge_loadtest
    OWNER ticketforge
    ENCODING 'UTF8';
```

Do not run load tests against the normal `ticketforge` database.

## Run Loadtest Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=loadtest"
```

`application-loadtest.yml` points to `ticketforge_loadtest` and extends reservation TTL to `PT30M`.

## Load-Test APIs

Only available in the `loadtest` profile:

```http
GET /api/load-test/profile
POST /api/load-test/reset
GET /api/load-test/state?eventSlug=ticketforge-load-test-live
```

All require:

```http
X-Load-Test-Secret: ticketforge-local-loadtest-secret
```

Reset affects only the dedicated load-test event, related orders/payments, and `loadtest-user-*@ticketforge.local` users.

## Observability

Actuator exposes:

```text
/actuator/health
/actuator/info
/actuator/metrics
/actuator/prometheus
```

Business metrics include order creation, idempotent replay, rejection, inventory reserve/release, payment success/failure, callback replay, and order/payment durations. Metric tags are intentionally low cardinality. Do not tag metrics with order numbers, payment transaction ids, emails, idempotency keys, provider event ids, or user ids.

## k6

Install native k6 on Windows:

```powershell
winget install k6 --source winget
k6 version
```

Run:

```powershell
cd load-tests
.\scripts\run-smoke.ps1
.\scripts\run-correctness.ps1
.\scripts\run-baseline.ps1
```

Scenarios:

- Smoke
- Order baseline
- Oversell spike
- Idempotency retry
- Payment callback replay
- Full journey

Strict correctness thresholds require zero oversell, zero inventory inconsistency, zero duplicate order processing, zero duplicate payment processing, and zero unexpected errors.

Provisional performance guardrails:

```text
http_req_duration p(95) < 3000ms
http_req_duration p(99) < 5000ms
```

These are provisional guardrails, not production SLOs.

`OUT_OF_STOCK` is an expected business result after stock is exhausted in spike scenarios. Other 409 responses, 5xx responses, invalid JSON, negative inventory, oversell, or invariant failures are test failures.

## Reports

Templates:

```text
docs/performance/baseline-template.md
load-tests/reports/baseline-template.md
```

Create `docs/performance/baseline.md` only after a real successful local run. Do not invent RPS, P50, P95, P99, or any performance number.

## GitHub Actions

- `ci.yml`: normal push and pull request checks.
- `performance.yml`: manual `workflow_dispatch` only. It runs low-scale k6 smoke/correctness against PostgreSQL 17 and uploads summaries. It is not a real production baseline.

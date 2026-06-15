# TicketForge

[中文 README](README.md)

TicketForge is a high-concurrency ticketing-system lab inspired by platforms such as ePlus, Ticket Pia, and Damai. Phase 1 focuses only on a reliable project foundation, database model, and the smallest event-query path.

## Current Phase

- Docker Compose starts PostgreSQL, Redis, and Adminer.
- Spring Boot uses Flyway to create the core schema and seed demo data.
- The backend exposes read-only event query APIs.
- The React + TypeScript frontend displays events, ticket tiers, prices, and inventory.
- Backend tests and frontend builds run in GitHub Actions.

## Why TicketForge

Real ticketing platforms must balance sudden traffic, inventory consistency, idempotency, payment callbacks, queueing, and user experience. TicketForge builds those concerns in phases. This first phase keeps the system runnable, testable, and migration-driven.

## Tech Stack

- Backend: Java 21, Spring Boot 3.5.15, Maven, Spring Web, Spring Data JPA, Validation, Redis, Actuator, Flyway, PostgreSQL Driver, JUnit 5, Mockito
- Frontend: React, TypeScript, Vite, npm, CSS
- Infrastructure: PostgreSQL, Redis, Adminer, Docker Compose

## Structure

```text
TicketForge/
├─ backend/
├─ frontend/
├─ load-tests/
├─ docs/
├─ compose.yaml
├─ .env.example
├─ README.md
└─ README.en.md
```

## Architecture

TicketForge is currently a modular monolith, not a microservice system. PostgreSQL is the source of truth for business data. Redis is started in Phase 1 but reserved for later queueing, temporary reservation, and idempotency-control work. Flyway owns database versioning. React is responsible only for the UI. See [docs/architecture.md](docs/architecture.md).

## Requirements

- Java 21
- Node.js LTS
- npm
- Docker Desktop or a compatible Docker Compose runtime
- Git

## Clash Proxy

If dependency downloads need a proxy, set these in the current terminal:

```powershell
$env:HTTP_PROXY="http://127.0.0.1:7890"
$env:HTTPS_PROXY="http://127.0.0.1:7890"
```

Repository-local Git proxy:

```bash
git config --local http.proxy http://127.0.0.1:7890
git config --local https.proxy http://127.0.0.1:7890
```

If Maven does not pick up environment variables, append:

```text
-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890
```

## Local Setup

Copy the environment template:

```bash
cp .env.example .env
```

Start infrastructure:

```bash
docker compose up -d
docker compose ps
```

Start the backend:

```bash
cd backend
./mvnw spring-boot:run
```

Windows:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Start the frontend:

```bash
cd frontend
npm install
npm run dev
```

Open:

- Frontend: http://localhost:5173
- Backend health: http://localhost:8080/actuator/health
- Events API: http://localhost:8080/api/events
- Adminer: http://localhost:8081

## Adminer Login

- System: PostgreSQL
- Server: `postgres`
- Username: `ticketforge`
- Password: `ticketforge_dev`
- Database: `ticketforge`

These credentials are for local development only.

## Flyway

Migration files live in:

```text
backend/src/main/resources/db/migration/
```

- `V1__create_core_schema.sql`: creates users, events, ticket tiers, inventory, orders, and payment records.
- `V2__seed_demo_data.sql`: creates demo users, one demo event, three ticket tiers, and inventory.

## API Examples

```http
GET /api/events
GET /api/events/{eventId}
GET /api/events/slug/{slug}
GET /actuator/health
```

Examples:

```bash
curl http://localhost:8080/api/events
curl http://localhost:8080/api/events/1
curl http://localhost:8080/api/events/slug/ticketforge-opening-live
```

## Not Implemented Yet

- Registration, login, and JWT
- Virtual queue
- Checkout
- Inventory deduction
- Payment
- Redis distributed locks and Lua
- Message queues
- k6 load tests
- WebSocket or SSE
- Microservices
- Kubernetes
- Seat selection

## Roadmap

- Phase 2: order creation, inventory reservation, and idempotency keys.
- Phase 3: Redis queueing, temporary stock locks, and timeout release.
- Phase 4: simulated payment callbacks, order state machine, and compensation.
- Phase 5: load testing, bottleneck analysis, and observability.

## Test Commands

Backend:

```bash
cd backend
./mvnw test
```

Frontend:

```bash
cd frontend
npm ci
npm run build
```

## Commit Convention

Use Conventional Commits:

```text
feat: add event query API
fix: correct inventory mapping
docs: update local setup guide
test: add event service coverage
chore: update CI workflow
```


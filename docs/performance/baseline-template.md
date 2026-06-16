# TicketForge PostgreSQL Baseline Template

Performance baseline not executed in the current environment.

## Environment

- Date:
- Commit SHA:
- OS:
- CPU:
- RAM:
- Java version:
- PostgreSQL version:
- k6 version:
- Spring profile: `loadtest`
- k6 and backend on same machine:
- PostgreSQL local:

## Scenarios

- Smoke
- Order baseline
- Oversell spike
- Idempotency retry
- Payment callback replay
- Full journey

## Result Fields Per Scenario

- Scale:
- Duration:
- Requests:
- RPS:
- P50:
- P90:
- P95:
- P99:
- Max latency:
- HTTP exceptions:
- Business exceptions:
- Dropped iterations:
- Correctness checks:

## Database Correctness

- Total stock:
- Available:
- Reserved:
- Sold:
- Successful orders:
- Out-of-stock count:
- Inventory invariant:
- Oversell detected:
- Negative inventory detected:

## Interpretation

These are provisional guardrails, not production SLOs.

`OUT_OF_STOCK` is an expected business result in oversell-spike scenarios when all stock has already been reserved. Other 409 responses, 5xx responses, invalid JSON, inventory inconsistency, negative inventory, duplicate order processing, or duplicate payment processing are failures.

## Limitations

- Single-machine local results do not represent production capacity.
- If k6 and the backend run on the same machine, they compete for CPU and memory.
- Redis is not used yet.
- Message queues are not used yet.
- Multi-instance deployment has not been tested.
- Cross-region network behavior has not been tested.
- Results are only a PostgreSQL baseline for future comparisons.

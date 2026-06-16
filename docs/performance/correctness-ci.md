# TicketForge Correctness CI Report

Generated at: 2026-06-16T11:09:00Z

This report is generated from local `load-tests/results/*-summary.json` files when they exist. Raw JSON files remain ignored and are not committed.

This is a small GitHub Actions correctness run, not a production capacity benchmark.

| Scenario | Checks pass rate | Business success | Orders created | OUT_OF_STOCK | Oversell | Inventory inconsistency | Duplicate orders | Duplicate payment processing | Unexpected errors | HTTP requests | RPS | P50 ms | P90 ms | P95 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| smoke | 1 | 1 | 1 | N/A | 0 | 0 | 0 | 0 | 0 | 10 | 25.93 | 24.36 | 83.75 | 92.85 |
| oversell spike | 1 | 1 | 20 | 30 | 0 | 0 | 0 | 0 | 0 | 53 | 141.11 | 229.00 | 318.22 | 329.27 |
| idempotency retry | 1 | 1 | 1 | N/A | 0 | 0 | 0 | 0 | 0 | 13 | 196.09 | 21.16 | 28.38 | 31.18 |
| payment callback replay | 1 | 1 | 1 | N/A | 0 | 0 | 0 | 0 | 0 | 15 | 161.88 | 12.76 | 17.14 | 19.71 |

The current local summaries include the expected correctness dimensions: smoke, oversell spike, idempotency retry and payment callback replay.

P99 is intentionally not reported unless it exists in the source summaries.

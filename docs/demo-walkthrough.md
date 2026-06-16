# TicketForge Demo Walkthrough

This walkthrough uses text steps because no final verified screenshot has been committed. Do not add placeholder screenshots.

## 1. Start Demo

```powershell
.\scripts\start-demo.ps1
```

The script checks the local environment, starts the backend with the `demo` profile, verifies `/actuator/health` and `/api/demo/profile`, then starts the Vite frontend.

## 2. Select Ticket Tier

Open `Purchase Demo`, choose `TicketForge Opening Live`, then select a ticket tier such as `VIP`, `S` or `A`.

The quantity selector is intentionally limited to 1 through 5 to match backend validation.

## 3. Reserve Tickets

Click `Reserve tickets`.

Expected result:

- A `PENDING_PAYMENT` order is created.
- Available stock decreases by the selected quantity.
- Reserved stock increases by the selected quantity.
- The operation timeline records the local UI action.

## 4. Observe Inventory

Use the `Inventory Overview` cards and `System Dashboard`.

The frontend does not guess inventory. It refreshes the backend event detail and demo dashboard after order, cancel and payment operations.

## 5. Simulate Payment Failure

Create a payment session, then click `Simulate payment failure`.

Expected result:

- The payment record becomes `FAILED`.
- The order remains `PENDING_PAYMENT`.
- Reserved stock remains reserved.
- The UI displays: `Payment failed. The order remains pending and stock remains reserved.`

## 6. Retry Payment Successfully

Create another payment session for the same pending order, then click `Simulate payment success`.

Expected result:

- The order becomes `PAID`.
- Reserved stock decreases.
- Sold stock increases.
- The UI displays: `Payment succeeded. Reserved stock has moved to sold stock.`

## 7. View Dashboard

Open `System Dashboard`.

It shows:

- Backend health
- Demo profile status
- Total, available, reserved and sold stock
- Order counts by status
- Payment counts by status
- Inventory consistency
- Recent 10 orders
- Per-tier inventory

## 8. Reset Demo Data

Click `Reset demo data` and confirm:

```text
This will delete demo orders and payment records. Continue?
```

Expected result:

- Demo orders and payment records for `ticketforge-opening-live` are deleted.
- Ticket inventory is restored to total stock.
- App users, event records, ticket tiers, Flyway history and non-demo event data are preserved.

## Screenshot Placeholder

Capture final screenshots after a successful local run and add them near the top of the README only after verifying that they are real, current and useful.

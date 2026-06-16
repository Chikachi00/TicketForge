import { DemoDashboard, EventDetail } from '../types';

type InventoryOverviewProps = {
  selectedEvent: EventDetail | null;
  dashboard: DemoDashboard | null;
};

export function InventoryOverview({ selectedEvent, dashboard }: InventoryOverviewProps) {
  const fromEvent = selectedEvent
    ? selectedEvent.ticketTiers.reduce(
        (acc, tier) => ({
          total: acc.total + tier.totalStock,
          available: acc.available + tier.availableStock,
          reserved: acc.reserved + tier.reservedStock,
          sold: acc.sold + tier.soldStock
        }),
        { total: 0, available: 0, reserved: 0, sold: 0 }
      )
    : null;
  const values = dashboard
    ? {
        total: dashboard.inventory.totalStock,
        available: dashboard.inventory.availableStock,
        reserved: dashboard.inventory.reservedStock,
        sold: dashboard.inventory.soldStock
      }
    : fromEvent;

  return (
    <section className="panel inventory-overview">
      <div className="section-heading">
        <h2>Inventory Overview</h2>
        <span>{dashboard?.inventory.inventoryConsistent ?? true ? 'consistent' : 'check needed'}</span>
      </div>
      {!values && <p className="state">Inventory will appear after the event loads.</p>}
      {values && (
        <div className="metric-grid">
          <Metric label="Total" value={values.total} />
          <Metric label="Available" value={values.available} />
          <Metric label="Reserved" value={values.reserved} />
          <Metric label="Sold" value={values.sold} />
        </div>
      )}
    </section>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="metric-card">
      <span>{label}</span>
      <strong>{value.toLocaleString()}</strong>
    </div>
  );
}

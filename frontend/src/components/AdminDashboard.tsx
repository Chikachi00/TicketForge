import { formatCurrency, formatDate } from '../format';
import { DemoDashboard, DemoProfile, LoadState } from '../types';
import { EmptyState } from './EmptyState';
import { StatusBadge } from './StatusBadge';

type AdminDashboardProps = {
  health: string;
  profile: DemoProfile | null;
  dashboard: DemoDashboard | null;
  state: LoadState;
  error: string | null;
  resetting: boolean;
  resetMessage: string | null;
  onRefresh: () => void;
  onReset: () => void;
};

export function AdminDashboard({
  health,
  profile,
  dashboard,
  state,
  error,
  resetting,
  resetMessage,
  onRefresh,
  onReset
}: AdminDashboardProps) {
  const demoEnabled = profile?.enabled && profile.profile === 'demo';

  return (
    <section className="dashboard-layout">
      <div className="panel dashboard-header">
        <div>
          <p className="eyebrow">System Dashboard</p>
          <h2>{dashboard?.event.name ?? 'Demo profile status'}</h2>
          <p className="state">
            Backend health: <strong>{health}</strong> / Demo profile: <strong>{profile?.profile ?? 'disabled'}</strong>
          </p>
        </div>
        <div className="dashboard-actions">
          <button className="secondary-action" type="button" onClick={onRefresh} disabled={state === 'loading'}>
            {state === 'loading' ? 'Refreshing...' : 'Refresh dashboard'}
          </button>
          {demoEnabled ? (
            <button className="danger-action" type="button" onClick={onReset} disabled={resetting}>
              {resetting ? 'Resetting...' : 'Reset demo data'}
            </button>
          ) : (
            <span className="state">Start the backend with the demo profile to enable reset and dashboard tools.</span>
          )}
        </div>
      </div>
      {error && <p className="state error">{error}</p>}
      {resetMessage && <p className="state success">{resetMessage}</p>}
      {!dashboard && state !== 'loading' && !error && <EmptyState title="Demo profile disabled" />}
      {dashboard && (
        <>
          <div className="metric-grid wide">
            <Metric label="Total stock" value={dashboard.inventory.totalStock} />
            <Metric label="Available" value={dashboard.inventory.availableStock} />
            <Metric label="Reserved" value={dashboard.inventory.reservedStock} />
            <Metric label="Sold" value={dashboard.inventory.soldStock} />
            <Metric label="Total orders" value={dashboard.orders.total} />
            <Metric label="Pending orders" value={dashboard.orders.pendingPayment} />
            <Metric label="Paid orders" value={dashboard.orders.paid} />
            <Metric label="Cancelled orders" value={dashboard.orders.cancelled} />
            <Metric label="Successful payments" value={dashboard.payments.success} />
            <Metric label="Failed payments" value={dashboard.payments.failed} />
          </div>
          <div className="panel">
            <div className="section-heading">
              <h2>Inventory consistency</h2>
              <StatusBadge status={dashboard.inventory.inventoryConsistent ? 'ON_SALE' : 'FAILED'} />
            </div>
            <p className="state">
              Calculated total: {dashboard.inventory.calculatedTotal.toLocaleString()} / Source total: {dashboard.inventory.totalStock.toLocaleString()}
            </p>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Tier</th>
                    <th>Total</th>
                    <th>Available</th>
                    <th>Reserved</th>
                    <th>Sold</th>
                    <th>Invariant</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboard.ticketTiers.map((tier) => (
                    <tr key={tier.code}>
                      <td>{tier.code} / {tier.name}</td>
                      <td>{tier.totalStock}</td>
                      <td>{tier.availableStock}</td>
                      <td>{tier.reservedStock}</td>
                      <td>{tier.soldStock}</td>
                      <td>{tier.inventoryConsistent ? 'OK' : 'Check'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <div className="panel">
            <div className="section-heading">
              <h2>Recent 10 orders</h2>
              <span>{formatDate(dashboard.generatedAt)}</span>
            </div>
            {dashboard.recentOrders.length === 0 && <EmptyState title="No recent orders" />}
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Order</th>
                    <th>Tier</th>
                    <th>Qty</th>
                    <th>Status</th>
                    <th>Total</th>
                    <th>Payment</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboard.recentOrders.map((order) => (
                    <tr key={order.orderNumber}>
                      <td>{order.orderNumber}</td>
                      <td>{order.ticketTierCode}</td>
                      <td>{order.quantity}</td>
                      <td>{order.status}</td>
                      <td>{formatCurrency(order.totalAmount)}</td>
                      <td>{order.latestPaymentStatus ?? 'N/A'}</td>
                      <td>{formatDate(order.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
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

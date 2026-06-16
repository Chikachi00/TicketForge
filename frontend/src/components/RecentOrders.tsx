import { formatCurrency, formatDate } from '../format';
import { OrderSummary } from '../types';
import { EmptyState } from './EmptyState';
import { StatusBadge } from './StatusBadge';

type RecentOrdersProps = {
  orders: OrderSummary[];
  onOpenOrder: (orderNumber: string) => void;
};

export function RecentOrders({ orders, onOpenOrder }: RecentOrdersProps) {
  return (
    <section className="panel recent-orders">
      <div className="section-heading">
        <h2>Recent Orders</h2>
        <span>{orders.length}</span>
      </div>
      {orders.length === 0 && <EmptyState title="No recent orders" />}
      <div className="order-list">
        {orders.map((order) => (
          <button
            key={order.orderNumber}
            className="order-list-item"
            type="button"
            onClick={() => onOpenOrder(order.orderNumber)}
          >
            <strong>{order.orderNumber}</strong>
            <span>
              {order.ticketTierCode} / {order.quantity} / {formatCurrency(order.totalAmount)}
            </span>
            <span>{formatDate(order.createdAt)}</span>
            <StatusBadge status={order.status} />
          </button>
        ))}
      </div>
    </section>
  );
}

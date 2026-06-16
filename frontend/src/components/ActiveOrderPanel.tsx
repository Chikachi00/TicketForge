import { formatCurrency, formatDate, formatRemaining } from '../format';
import { OrderResponse, PaymentQueryResponse, PaymentSessionResponse } from '../types';
import { EmptyState } from './EmptyState';
import { PaymentPanel } from './PaymentPanel';
import { StatusBadge } from './StatusBadge';

type ActiveOrderPanelProps = {
  activeOrder: OrderResponse | null;
  paymentSession: PaymentSessionResponse | null;
  settledPayment: PaymentQueryResponse | null;
  orderError: string | null;
  paymentMessage: string | null;
  paymentBusy: boolean;
  cancelling: boolean;
  remainingMs: number;
  onCancel: () => void;
  onCreatePayment: () => void;
  onSimulateSuccess: () => void;
  onSimulateFailure: () => void;
};

export function ActiveOrderPanel({
  activeOrder,
  paymentSession,
  settledPayment,
  orderError,
  paymentMessage,
  paymentBusy,
  cancelling,
  remainingMs,
  onCancel,
  onCreatePayment,
  onSimulateSuccess,
  onSimulateFailure
}: ActiveOrderPanelProps) {
  return (
    <section className="panel active-order-panel" aria-live="polite">
      <div className="section-heading">
        <h2>Active Order</h2>
        <span>{activeOrder?.status ?? 'none'}</span>
      </div>
      {orderError && <p className="state error">{orderError}</p>}
      {!activeOrder && <EmptyState title="No active order" detail="Reserve a ticket tier to create a pending order." />}
      {activeOrder && (
        <article className="active-order">
          <StatusBadge status={activeOrder.status} />
          <strong>{activeOrder.orderNumber}</strong>
          <dl>
            <div>
              <dt>Tier</dt>
              <dd>{activeOrder.ticketTierCode} / {activeOrder.ticketTierName}</dd>
            </div>
            <div>
              <dt>Quantity</dt>
              <dd>{activeOrder.quantity}</dd>
            </div>
            <div>
              <dt>Total</dt>
              <dd>{formatCurrency(activeOrder.totalAmount)}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>{activeOrder.status}</dd>
            </div>
            <div>
              <dt>Created</dt>
              <dd>{formatDate(activeOrder.createdAt)}</dd>
            </div>
            <div>
              <dt>Expires</dt>
              <dd>{formatDate(activeOrder.expiresAt)}</dd>
            </div>
            <div>
              <dt>Countdown</dt>
              <dd>{activeOrder.status === 'PENDING_PAYMENT' ? formatRemaining(remainingMs) : 'N/A'}</dd>
            </div>
            <div>
              <dt>Payment transaction</dt>
              <dd>{paymentSession?.paymentTransactionId ?? settledPayment?.paymentTransactionId ?? 'N/A'}</dd>
            </div>
            <div>
              <dt>Paid at</dt>
              <dd>{formatDate(activeOrder.paidAt)}</dd>
            </div>
            <div>
              <dt>Cancelled at</dt>
              <dd>{formatDate(activeOrder.cancelledAt)}</dd>
            </div>
          </dl>
          {activeOrder.idempotentReplay && <p className="state">Returned from an idempotent replay.</p>}
          {activeOrder.status === 'PENDING_PAYMENT' && (
            <>
              <PaymentPanel
                paymentSession={paymentSession}
                settledPayment={settledPayment}
                paymentMessage={paymentMessage}
                busy={paymentBusy}
                onCreatePayment={onCreatePayment}
                onSimulateSuccess={onSimulateSuccess}
                onSimulateFailure={onSimulateFailure}
              />
              <button
                className="secondary-action full-width-action"
                type="button"
                onClick={onCancel}
                disabled={cancelling || paymentBusy}
              >
                {cancelling ? 'Cancelling...' : 'Cancel order'}
              </button>
            </>
          )}
          {activeOrder.status === 'PAID' && <p className="state success">Payment succeeded. Reserved stock has moved to sold stock.</p>}
          {activeOrder.status === 'CANCELLED' && <p className="state">Inventory has been released.</p>}
        </article>
      )}
    </section>
  );
}

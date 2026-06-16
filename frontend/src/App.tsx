import { useEffect, useMemo, useState } from 'react';
import {
  EventDetail,
  EventSummary,
  OrderResponse,
  OrderSummary,
  PaymentQueryResponse,
  PaymentSessionResponse,
  cancelOrder,
  createOrder,
  createPaymentSession,
  fetchEvent,
  fetchEvents,
  fetchMyOrders,
  fetchOrder,
  fetchPayment,
  simulatePaymentFailure,
  simulatePaymentSuccess
} from './api';
import './App.css';

type LoadState = 'idle' | 'loading' | 'loaded' | 'empty' | 'error';

type ReservationIntent = {
  ticketTierId: number;
  quantity: number;
  idempotencyKey: string;
};

const currencyFormatter = new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short'
});

function formatDate(value: string) {
  return dateFormatter.format(new Date(value));
}

function formatRemaining(milliseconds: number) {
  const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

function App() {
  const [events, setEvents] = useState<EventSummary[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<EventDetail | null>(null);
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [activeOrder, setActiveOrder] = useState<OrderResponse | null>(null);
  const [paymentSession, setPaymentSession] = useState<PaymentSessionResponse | null>(null);
  const [settledPayment, setSettledPayment] = useState<PaymentQueryResponse | null>(null);
  const [quantities, setQuantities] = useState<Record<number, number>>({});
  const [submittingTierId, setSubmittingTierId] = useState<number | null>(null);
  const [failedIntent, setFailedIntent] = useState<ReservationIntent | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [paymentBusy, setPaymentBusy] = useState(false);
  const [nowMs, setNowMs] = useState(Date.now());
  const [listState, setListState] = useState<LoadState>('idle');
  const [detailState, setDetailState] = useState<LoadState>('idle');
  const [orderState, setOrderState] = useState<LoadState>('idle');
  const [error, setError] = useState<string | null>(null);
  const [orderError, setOrderError] = useState<string | null>(null);
  const [paymentMessage, setPaymentMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setListState('loading');
    fetchEvents()
      .then((data) => {
        if (!active) return;
        setEvents(data);
        setSelectedId(data[0]?.id ?? null);
        setListState(data.length === 0 ? 'empty' : 'loaded');
      })
      .catch(() => {
        if (!active) return;
        setError('Unable to load events from the backend.');
        setListState('error');
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    fetchMyOrders()
      .then((data) => {
        if (active) setOrders(data);
      })
      .catch(() => {
        if (active) setOrderError('Unable to load Demo User orders.');
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (selectedId === null) {
      setSelectedEvent(null);
      return;
    }

    let active = true;
    setDetailState('loading');
    fetchEvent(selectedId)
      .then((data) => {
        if (!active) return;
        setSelectedEvent(data);
        setDetailState('loaded');
      })
      .catch(() => {
        if (!active) return;
        setError('Unable to load ticket tiers for the selected event.');
        setDetailState('error');
      });
    return () => {
      active = false;
    };
  }, [selectedId]);

  useEffect(() => {
    const timer = window.setInterval(() => setNowMs(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!activeOrder || activeOrder.status !== 'PENDING_PAYMENT') return undefined;

    const interval = window.setInterval(() => {
      fetchOrder(activeOrder.orderNumber)
        .then((updatedOrder) => {
          setActiveOrder(updatedOrder);
          if (updatedOrder.status === 'CANCELLED') {
            refreshCurrentEvent();
            refreshOrders();
          }
        })
        .catch(() => setOrderError('Unable to refresh the current order.'));
    }, 5000);

    return () => window.clearInterval(interval);
  }, [activeOrder?.orderNumber, activeOrder?.status]);

  const totalAvailable = useMemo(
    () => selectedEvent?.ticketTiers.reduce((sum, tier) => sum + tier.availableStock, 0) ?? 0,
    [selectedEvent]
  );

  const remainingMs = activeOrder ? new Date(activeOrder.expiresAt).getTime() - nowMs : 0;

  function setTierQuantity(ticketTierId: number, quantity: number) {
    setQuantities((current) => ({ ...current, [ticketTierId]: quantity }));
  }

  function refreshCurrentEvent() {
    if (selectedId === null) return;
    fetchEvent(selectedId)
      .then(setSelectedEvent)
      .catch(() => setOrderError('Unable to refresh inventory.'));
    fetchEvents()
      .then(setEvents)
      .catch(() => undefined);
  }

  function refreshOrders() {
    fetchMyOrders()
      .then(setOrders)
      .catch(() => setOrderError('Unable to refresh orders.'));
  }

  function reserveTier(ticketTierId: number, existingIntent?: ReservationIntent) {
    const quantity = existingIntent?.quantity ?? quantities[ticketTierId] ?? 1;
    const idempotencyKey = existingIntent?.idempotencyKey ?? crypto.randomUUID();
    setSubmittingTierId(ticketTierId);
    setOrderState('loading');
    setOrderError(null);
    setFailedIntent(null);

    createOrder(ticketTierId, quantity, idempotencyKey)
      .then((order) => {
        setActiveOrder(order);
        setPaymentSession(null);
        setSettledPayment(null);
        setPaymentMessage(null);
        setOrderState('loaded');
        refreshCurrentEvent();
        refreshOrders();
      })
      .catch((exception: Error) => {
        setFailedIntent({ ticketTierId, quantity, idempotencyKey });
        setOrderError(exception.message);
        setOrderState('error');
      })
      .finally(() => setSubmittingTierId(null));
  }

  function cancelActiveOrder() {
    if (!activeOrder) return;
    setCancelling(true);
    setOrderError(null);
    cancelOrder(activeOrder.orderNumber)
      .then((order) => {
        setActiveOrder(order);
        setPaymentSession(null);
        setSettledPayment(null);
        setPaymentMessage(null);
        refreshCurrentEvent();
        refreshOrders();
      })
      .catch((exception: Error) => setOrderError(exception.message))
      .finally(() => setCancelling(false));
  }

  function beginPayment() {
    if (!activeOrder) return;
    setPaymentBusy(true);
    setOrderError(null);
    setPaymentMessage(null);
    createPaymentSession(activeOrder.orderNumber, crypto.randomUUID())
      .then((session) => {
        setPaymentSession(session);
        setPaymentMessage('Payment session is ready in the local simulator.');
      })
      .catch((exception: Error) => setOrderError(exception.message))
      .finally(() => setPaymentBusy(false));
  }

  function settlePayment(result: 'success' | 'failure') {
    if (!paymentSession) return;
    setPaymentBusy(true);
    setOrderError(null);
    setPaymentMessage(null);
    const action = result === 'success' ? simulatePaymentSuccess : simulatePaymentFailure;
    action(paymentSession.paymentTransactionId)
      .then(() => Promise.all([
        fetchOrder(paymentSession.orderNumber),
        fetchPayment(paymentSession.paymentTransactionId).catch(() => null)
      ]))
      .then(([order, payment]) => {
        setActiveOrder(order);
        setSettledPayment(payment);
        if (order.status === 'PAID') {
          setPaymentMessage('Payment succeeded. Reserved stock has been converted to sold stock.');
        } else {
          setPaymentMessage('Payment failed. The order remains pending until it is paid, cancelled, or expired.');
          setPaymentSession(null);
        }
        refreshCurrentEvent();
        refreshOrders();
      })
      .catch((exception: Error) => setOrderError(exception.message))
      .finally(() => setPaymentBusy(false));
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">TicketForge</p>
          <h1>TicketForge</h1>
          <p className="subtitle">High-concurrency ticketing system lab</p>
        </div>
        <div className="user-badge">
          <span>Demo User</span>
          <strong>user@ticketforge.local</strong>
        </div>
      </header>

      <section className="content-grid">
        <aside className="event-list" aria-label="Events">
          <div className="section-heading">
            <h2>Events</h2>
            <span>{events.length}</span>
          </div>

          {listState === 'loading' && <p className="state">Loading events...</p>}
          {listState === 'empty' && <p className="state">No events are available.</p>}
          {listState === 'error' && <p className="state error">{error}</p>}

          <div className="event-stack">
            {events.map((event) => (
              <button
                className={event.id === selectedId ? 'event-card active' : 'event-card'}
                key={event.id}
                onClick={() => setSelectedId(event.id)}
                type="button"
              >
                <span className="status">{event.status}</span>
                <strong>{event.name}</strong>
                <span>{event.venue}</span>
                <span>{formatDate(event.performanceAt)}</span>
              </button>
            ))}
          </div>
        </aside>

        <section className="detail-panel" aria-live="polite">
          {detailState === 'loading' && <p className="state">Loading ticket tiers...</p>}
          {detailState === 'error' && <p className="state error">{error}</p>}

          {detailState === 'loaded' && selectedEvent && (
            <>
              <div className="event-hero">
                <span className="status">{selectedEvent.status}</span>
                <h2>{selectedEvent.name}</h2>
                <p>{selectedEvent.description}</p>
                <div className="metadata-grid">
                  <div>
                    <span>Venue</span>
                    <strong>{selectedEvent.venue}</strong>
                  </div>
                  <div>
                    <span>Performance</span>
                    <strong>{formatDate(selectedEvent.performanceAt)}</strong>
                  </div>
                  <div>
                    <span>Sales start</span>
                    <strong>{formatDate(selectedEvent.salesStartAt)}</strong>
                  </div>
                  <div>
                    <span>Available</span>
                    <strong>{totalAvailable.toLocaleString()}</strong>
                  </div>
                </div>
              </div>

              <div className="tiers">
                {selectedEvent.ticketTiers.map((tier) => {
                  const quantity = quantities[tier.id] ?? 1;
                  const isSubmitting = submittingTierId === tier.id;
                  return (
                    <article className="tier-row" key={tier.id}>
                      <div>
                        <span className="tier-code">{tier.code}</span>
                        <h3>{tier.name}</h3>
                      </div>
                      <div className="purchase-controls">
                        <div className="tier-metrics">
                          <span>{currencyFormatter.format(tier.price)}</span>
                          <strong>{tier.availableStock.toLocaleString()} left</strong>
                        </div>
                        <label>
                          Qty
                          <select
                            value={quantity}
                            onChange={(event) => setTierQuantity(tier.id, Number(event.target.value))}
                            disabled={isSubmitting}
                          >
                            {[1, 2, 3, 4, 5, 6].map((value) => (
                              <option key={value} value={value}>
                                {value}
                              </option>
                            ))}
                          </select>
                        </label>
                        <button
                          className="primary-action"
                          type="button"
                          onClick={() => reserveTier(tier.id)}
                          disabled={isSubmitting || tier.availableStock <= 0}
                        >
                          {isSubmitting ? 'Processing...' : 'Reserve tickets'}
                        </button>
                      </div>
                    </article>
                  );
                })}
              </div>
            </>
          )}
        </section>

        <aside className="order-panel" aria-live="polite">
          <div className="section-heading">
            <h2>Current Order</h2>
            <span>{orderState === 'loading' ? 'working' : activeOrder?.status ?? 'none'}</span>
          </div>
          {orderError && <p className="state error">{orderError}</p>}
          {failedIntent && (
            <button
              className="secondary-action retry-action"
              type="button"
              onClick={() => reserveTier(failedIntent.ticketTierId, failedIntent)}
              disabled={submittingTierId !== null}
            >
              Retry reservation
            </button>
          )}
          {!activeOrder && <p className="state">Reserve a ticket tier to create a pending order.</p>}
          {activeOrder && (
            <article className="active-order">
              <span className="status">{activeOrder.status}</span>
              <strong>{activeOrder.orderNumber}</strong>
              <dl>
                <div>
                  <dt>Tier</dt>
                  <dd>{activeOrder.ticketTierName}</dd>
                </div>
                <div>
                  <dt>Quantity</dt>
                  <dd>{activeOrder.quantity}</dd>
                </div>
                <div>
                  <dt>Total</dt>
                  <dd>{currencyFormatter.format(activeOrder.totalAmount)}</dd>
                </div>
                <div>
                  <dt>Countdown</dt>
                  <dd>{activeOrder.status === 'PENDING_PAYMENT' ? formatRemaining(remainingMs) : activeOrder.status === 'PAID' ? 'Paid' : 'Released'}</dd>
                </div>
                {activeOrder.paidAt && (
                  <div>
                    <dt>Paid at</dt>
                    <dd>{formatDate(activeOrder.paidAt)}</dd>
                  </div>
                )}
              </dl>
              {activeOrder.idempotentReplay && <p className="state">Returned from an idempotent replay.</p>}
              {activeOrder.status === 'CANCELLED' && <p className="state">Inventory has been released.</p>}
              {activeOrder.status === 'PAID' && <p className="state">Ticket sold. Reserved stock is now sold stock.</p>}
              {activeOrder.status === 'PENDING_PAYMENT' && (
                <>
                  <button
                    className="primary-action full-width-action"
                    type="button"
                    onClick={beginPayment}
                    disabled={paymentBusy}
                  >
                    {paymentBusy && !paymentSession ? 'Creating payment...' : '模拟支付'}
                  </button>
                  {paymentSession && (
                    <div className="payment-box">
                      <span className="tier-code">Simulator</span>
                      <dl>
                        <div>
                          <dt>Transaction</dt>
                          <dd>{paymentSession.paymentTransactionId}</dd>
                        </div>
                        <div>
                          <dt>Amount</dt>
                          <dd>{currencyFormatter.format(paymentSession.amount)}</dd>
                        </div>
                        <div>
                          <dt>Currency</dt>
                          <dd>{paymentSession.currency}</dd>
                        </div>
                      </dl>
                      <div className="payment-actions">
                        <button
                          className="primary-action"
                          type="button"
                          onClick={() => settlePayment('success')}
                          disabled={paymentBusy}
                        >
                          Simulate success
                        </button>
                        <button
                          className="secondary-action"
                          type="button"
                          onClick={() => settlePayment('failure')}
                          disabled={paymentBusy}
                        >
                          Simulate failure
                        </button>
                      </div>
                    </div>
                  )}
                  <button
                    className="secondary-action"
                    type="button"
                    onClick={cancelActiveOrder}
                    disabled={cancelling || paymentBusy}
                  >
                    {cancelling ? 'Cancelling...' : 'Cancel order'}
                  </button>
                </>
              )}
              {paymentMessage && <p className="state">{paymentMessage}</p>}
              {settledPayment?.processedAt && (
                <p className="state">
                  {settledPayment.status} via {settledPayment.paymentTransactionId} at {formatDate(settledPayment.processedAt)}
                </p>
              )}
            </article>
          )}

          <div className="section-heading orders-heading">
            <h2>My Orders</h2>
            <span>{orders.length}</span>
          </div>
          <div className="order-list">
            {orders.length === 0 && <p className="state">No recent orders.</p>}
            {orders.map((order) => (
              <button
                key={order.orderNumber}
                className="order-list-item"
                type="button"
                onClick={() => fetchOrder(order.orderNumber)
                  .then((loadedOrder) => {
                    setActiveOrder(loadedOrder);
                    setPaymentSession(null);
                    setSettledPayment(null);
                    setPaymentMessage(null);
                  })
                  .catch(() => undefined)}
              >
                <strong>{order.orderNumber}</strong>
                <span>
                  {order.ticketTierName} / {order.quantity} / {currencyFormatter.format(order.totalAmount)}
                </span>
                <span>{order.status}</span>
              </button>
            ))}
          </div>
        </aside>
      </section>
    </main>
  );
}

export default App;

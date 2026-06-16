import { useCallback, useEffect, useState } from 'react';
import {
  cancelOrder,
  createOrder,
  createPaymentSession,
  fetchMyOrders,
  fetchOrder,
  fetchPayment,
  simulatePaymentFailure,
  simulatePaymentSuccess
} from './api';
import './App.css';
import { ActiveOrderPanel } from './components/ActiveOrderPanel';
import { AdminDashboard } from './components/AdminDashboard';
import { AppHeader } from './components/AppHeader';
import { DemoGuide } from './components/DemoGuide';
import { EventDetail } from './components/EventDetail';
import { EventList } from './components/EventList';
import { HowItWorks } from './components/HowItWorks';
import { InventoryOverview } from './components/InventoryOverview';
import { OperationTimeline } from './components/OperationTimeline';
import { RecentOrders } from './components/RecentOrders';
import { useActiveOrderPolling } from './hooks/useActiveOrder';
import { useDemoDashboard } from './hooks/useDemoDashboard';
import { useEvents } from './hooks/useEvents';
import {
  LoadState,
  OrderResponse,
  OrderSummary,
  PaymentQueryResponse,
  PaymentSessionResponse,
  ReservationIntent,
  TabKey,
  TimelineEntry
} from './types';

function App() {
  const [activeTab, setActiveTab] = useState<TabKey>('purchase');
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [activeOrder, setActiveOrder] = useState<OrderResponse | null>(null);
  const [paymentSession, setPaymentSession] = useState<PaymentSessionResponse | null>(null);
  const [settledPayment, setSettledPayment] = useState<PaymentQueryResponse | null>(null);
  const [quantities, setQuantities] = useState<Record<number, number>>({});
  const [submittingTierId, setSubmittingTierId] = useState<number | null>(null);
  const [failedIntent, setFailedIntent] = useState<ReservationIntent | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [paymentBusy, setPaymentBusy] = useState(false);
  const [orderState, setOrderState] = useState<LoadState>('idle');
  const [orderError, setOrderError] = useState<string | null>(null);
  const [paymentMessage, setPaymentMessage] = useState<string | null>(null);
  const [timeline, setTimeline] = useState<TimelineEntry[]>([]);

  const events = useEvents();
  const demo = useDemoDashboard();

  const refreshOrders = useCallback(() => {
    return fetchMyOrders()
      .then(setOrders)
      .catch(() => setOrderError('Unable to refresh orders.'));
  }, []);

  useEffect(() => {
    refreshOrders();
  }, [refreshOrders]);

  const refreshInventoryViews = useCallback(() => {
    events.refreshSelectedEvent().catch(() => undefined);
    events.refreshEvents().catch(() => undefined);
    demo.refreshDashboard().catch(() => undefined);
  }, [demo, events]);

  const addTimeline = useCallback((message: string) => {
    setTimeline((current) => [
      {
        id: crypto.randomUUID(),
        at: new Intl.DateTimeFormat(undefined, { timeStyle: 'short' }).format(new Date()),
        message
      },
      ...current
    ].slice(0, 12));
  }, []);

  const nowMs = useActiveOrderPolling(
    activeOrder,
    setActiveOrder,
    () => {
      refreshInventoryViews();
      refreshOrders();
      addTimeline('Inventory refreshed');
    },
    setOrderError
  );

  const remainingMs = activeOrder ? new Date(activeOrder.expiresAt).getTime() - nowMs : 0;

  function setTierQuantity(ticketTierId: number, quantity: number) {
    setQuantities((current) => ({ ...current, [ticketTierId]: quantity }));
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
        addTimeline(`${order.idempotentReplay ? 'Replayed' : 'Reserved'} ${quantity} ${order.ticketTierCode} ticket`);
        refreshInventoryViews();
        refreshOrders();
      })
      .catch((exception: Error) => {
        setFailedIntent({ ticketTierId, quantity, idempotencyKey });
        setOrderError(exception.message);
        setOrderState('error');
        addTimeline('Reservation failed');
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
        addTimeline('Order cancelled and inventory refreshed');
        refreshInventoryViews();
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
        setSettledPayment(null);
        setPaymentMessage('Payment session created.');
        addTimeline('Payment session created');
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
          setPaymentMessage('Payment succeeded. Reserved stock has moved to sold stock.');
          addTimeline('Payment succeeded');
        } else {
          setPaymentMessage('Payment failed. The order remains pending and stock remains reserved.');
          setPaymentSession(null);
          addTimeline('Payment failed');
        }
        refreshInventoryViews();
        refreshOrders();
      })
      .catch((exception: Error) => setOrderError(exception.message))
      .finally(() => setPaymentBusy(false));
  }

  function openOrder(orderNumber: string) {
    fetchOrder(orderNumber)
      .then((loadedOrder) => {
        setActiveOrder(loadedOrder);
        setPaymentSession(null);
        setSettledPayment(null);
        setPaymentMessage(null);
      })
      .catch(() => setOrderError('Unable to load the selected order.'));
  }

  function resetDemoData() {
    if (!window.confirm('This will delete demo orders and payment records. Continue?')) return;
    demo.resetDashboard()
      .then(() => {
        setActiveOrder(null);
        setPaymentSession(null);
        setSettledPayment(null);
        setPaymentMessage(null);
        setFailedIntent(null);
        addTimeline('Demo data reset');
        refreshInventoryViews();
        refreshOrders();
      })
      .catch((exception: Error) => setOrderError(exception.message));
  }

  return (
    <main className="app-shell">
      <AppHeader activeTab={activeTab} onTabChange={setActiveTab} />

      {activeTab === 'purchase' && (
        <>
          <DemoGuide />
          <section className="purchase-layout">
            <EventList
              events={events.events}
              selectedId={events.selectedId}
              state={events.listState}
              error={events.error}
              onSelect={events.setSelectedId}
            />
            <div className="main-column">
              <InventoryOverview selectedEvent={events.selectedEvent} dashboard={demo.dashboard} />
              <EventDetail
                event={events.selectedEvent}
                state={events.detailState}
                error={events.error}
                quantities={quantities}
                submittingTierId={submittingTierId}
                onQuantityChange={setTierQuantity}
                onReserve={reserveTier}
              />
              {failedIntent && (
                <button
                  className="secondary-action full-width-action"
                  type="button"
                  onClick={() => reserveTier(failedIntent.ticketTierId, failedIntent)}
                  disabled={submittingTierId !== null || orderState === 'loading'}
                >
                  Retry failed request with same idempotency key
                </button>
              )}
            </div>
            <div className="side-column">
              <ActiveOrderPanel
                activeOrder={activeOrder}
                paymentSession={paymentSession}
                settledPayment={settledPayment}
                orderError={orderError}
                paymentMessage={paymentMessage}
                paymentBusy={paymentBusy}
                cancelling={cancelling}
                remainingMs={remainingMs}
                onCancel={cancelActiveOrder}
                onCreatePayment={beginPayment}
                onSimulateSuccess={() => settlePayment('success')}
                onSimulateFailure={() => settlePayment('failure')}
              />
              <RecentOrders orders={orders} onOpenOrder={openOrder} />
              <OperationTimeline entries={timeline} />
            </div>
          </section>
        </>
      )}

      {activeTab === 'dashboard' && (
        <AdminDashboard
          health={demo.health}
          profile={demo.profile}
          dashboard={demo.dashboard}
          state={demo.state}
          error={demo.error}
          resetting={demo.resetting}
          resetMessage={demo.resetMessage}
          onRefresh={() => {
            demo.refreshDashboard().catch(() => undefined);
            refreshOrders();
          }}
          onReset={resetDemoData}
        />
      )}

      {activeTab === 'how' && <HowItWorks />}
    </main>
  );
}

export default App;

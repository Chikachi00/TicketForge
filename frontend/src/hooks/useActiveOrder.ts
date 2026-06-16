import { useEffect, useState } from 'react';
import { fetchOrder } from '../api';
import { OrderResponse } from '../types';

export function useActiveOrderPolling(
  activeOrder: OrderResponse | null,
  onOrderUpdate: (order: OrderResponse) => void,
  onRefreshNeeded: () => void,
  onError: (message: string) => void
) {
  const [nowMs, setNowMs] = useState(Date.now());

  useEffect(() => {
    const timer = window.setInterval(() => setNowMs(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!activeOrder || activeOrder.status !== 'PENDING_PAYMENT') return undefined;

    const interval = window.setInterval(() => {
      fetchOrder(activeOrder.orderNumber)
        .then((updatedOrder) => {
          onOrderUpdate(updatedOrder);
          if (updatedOrder.status !== 'PENDING_PAYMENT') {
            onRefreshNeeded();
          }
        })
        .catch(() => onError('Unable to refresh the current order.'));
    }, 5000);

    return () => window.clearInterval(interval);
  }, [activeOrder?.orderNumber, activeOrder?.status, onError, onOrderUpdate, onRefreshNeeded]);

  return nowMs;
}

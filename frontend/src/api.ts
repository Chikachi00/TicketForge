export type TicketTierSummary = {
  id: number;
  code: string;
  name: string;
  price: number;
  totalStock: number;
  availableStock: number;
};

export type TicketTierDetail = TicketTierSummary & {
  reservedStock: number;
  soldStock: number;
};

export type EventSummary = {
  id: number;
  slug: string;
  name: string;
  venue: string;
  description: string | null;
  performanceAt: string;
  salesStartAt: string;
  status: string;
  ticketTiers: TicketTierSummary[];
};

export type EventDetail = Omit<EventSummary, 'ticketTiers'> & {
  ticketTiers: TicketTierDetail[];
};

export type OrderResponse = {
  orderNumber: string;
  eventId: number;
  eventName: string;
  ticketTierId: number;
  ticketTierCode: string;
  ticketTierName: string;
  quantity: number;
  unitPrice: number;
  totalAmount: number;
  status: string;
  expiresAt: string;
  cancelledAt: string | null;
  paidAt: string | null;
  createdAt: string;
  idempotentReplay: boolean;
};

export type OrderSummary = {
  orderNumber: string;
  eventName: string;
  ticketTierCode: string;
  ticketTierName: string;
  quantity: number;
  totalAmount: number;
  status: string;
  expiresAt: string;
  cancelledAt: string | null;
  paidAt: string | null;
  createdAt: string;
};

const DEMO_USER_EMAIL = 'user@ticketforge.local';

export type PaymentSessionResponse = {
  paymentTransactionId: string;
  orderNumber: string;
  amount: number;
  currency: string;
  status: string;
  createdAt: string;
};

export type PaymentQueryResponse = PaymentSessionResponse & {
  provider: string;
  processedAt: string | null;
};

export type PaymentCallbackResponse = {
  providerEventId: string;
  paymentTransactionId: string;
  orderNumber: string;
  status: string;
  idempotentReplay: boolean;
  processedAt: string | null;
};

async function requestJson<T>(path: string): Promise<T> {
  const response = await fetch(path, {
    headers: {
      Accept: 'application/json'
    }
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }

  return response.json() as Promise<T>;
}

async function sendJson<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(path, init);

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const error = (await response.json()) as { message?: string; code?: string };
      message = error.code ? `${error.code}: ${error.message ?? message}` : (error.message ?? message);
    } catch {
      // Keep the generic status message when the response is not JSON.
    }
    throw new Error(message);
  }

  return response.json() as Promise<T>;
}

export function fetchEvents(): Promise<EventSummary[]> {
  return requestJson<EventSummary[]>('/api/events');
}

export function fetchEvent(eventId: number): Promise<EventDetail> {
  return requestJson<EventDetail>(`/api/events/${eventId}`);
}

export function createOrder(ticketTierId: number, quantity: number, idempotencyKey: string): Promise<OrderResponse> {
  return sendJson<OrderResponse>('/api/orders', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-User-Email': DEMO_USER_EMAIL,
      'Idempotency-Key': idempotencyKey
    },
    body: JSON.stringify({ ticketTierId, quantity })
  });
}

export function fetchOrder(orderNumber: string): Promise<OrderResponse> {
  return sendJson<OrderResponse>(`/api/orders/${orderNumber}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      'X-User-Email': DEMO_USER_EMAIL
    }
  });
}

export function fetchMyOrders(): Promise<OrderSummary[]> {
  return sendJson<OrderSummary[]>('/api/orders/me', {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      'X-User-Email': DEMO_USER_EMAIL
    }
  });
}

export function cancelOrder(orderNumber: string): Promise<OrderResponse> {
  return sendJson<OrderResponse>(`/api/orders/${orderNumber}/cancel`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'X-User-Email': DEMO_USER_EMAIL
    }
  });
}

export function createPaymentSession(orderNumber: string, idempotencyKey: string): Promise<PaymentSessionResponse> {
  return sendJson<PaymentSessionResponse>(`/api/payments/orders/${orderNumber}`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'X-User-Email': DEMO_USER_EMAIL,
      'Idempotency-Key': idempotencyKey
    }
  });
}

export function fetchPayment(paymentTransactionId: string): Promise<PaymentQueryResponse> {
  return sendJson<PaymentQueryResponse>(`/api/payments/${paymentTransactionId}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      'X-User-Email': DEMO_USER_EMAIL
    }
  });
}

export function simulatePaymentSuccess(paymentTransactionId: string): Promise<PaymentCallbackResponse> {
  return sendJson<PaymentCallbackResponse>(`/api/payment-simulator/${paymentTransactionId}/success`, {
    method: 'POST',
    headers: {
      Accept: 'application/json'
    }
  });
}

export function simulatePaymentFailure(paymentTransactionId: string): Promise<PaymentCallbackResponse> {
  return sendJson<PaymentCallbackResponse>(`/api/payment-simulator/${paymentTransactionId}/failure`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ reason: 'SIMULATED_PAYMENT_DECLINED' })
  });
}

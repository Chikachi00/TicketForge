import {
  DemoDashboard,
  DemoProfile,
  DemoResetResponse,
  EventDetail,
  EventSummary,
  OrderResponse,
  OrderSummary,
  PaymentCallbackResponse,
  PaymentQueryResponse,
  PaymentSessionResponse
} from './types';

export const DEMO_USER_EMAIL = 'user@ticketforge.local';
export const DEMO_SECRET = import.meta.env.VITE_DEMO_SECRET ?? 'ticketforge-local-demo-secret';

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...init?.headers
    }
  });

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

function jsonHeaders(extra?: HeadersInit): HeadersInit {
  return {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    ...extra
  };
}

function userHeaders(extra?: HeadersInit): HeadersInit {
  return jsonHeaders({
    'X-User-Email': DEMO_USER_EMAIL,
    ...extra
  });
}

function demoHeaders(): HeadersInit {
  return {
    Accept: 'application/json',
    'X-Demo-Secret': DEMO_SECRET
  };
}

export function fetchEvents(): Promise<EventSummary[]> {
  return requestJson<EventSummary[]>('/api/events');
}

export function fetchEvent(eventId: number): Promise<EventDetail> {
  return requestJson<EventDetail>(`/api/events/${eventId}`);
}

export function fetchHealth(): Promise<{ status: string }> {
  return requestJson<{ status: string }>('/actuator/health');
}

export function fetchDemoProfile(): Promise<DemoProfile> {
  return requestJson<DemoProfile>('/api/demo/profile', {
    headers: demoHeaders()
  });
}

export function fetchDemoDashboard(): Promise<DemoDashboard> {
  return requestJson<DemoDashboard>('/api/demo/dashboard', {
    headers: demoHeaders()
  });
}

export function resetDemoData(): Promise<DemoResetResponse> {
  return requestJson<DemoResetResponse>('/api/demo/reset', {
    method: 'POST',
    headers: demoHeaders()
  });
}

export function createOrder(ticketTierId: number, quantity: number, idempotencyKey: string): Promise<OrderResponse> {
  return requestJson<OrderResponse>('/api/orders', {
    method: 'POST',
    headers: userHeaders({ 'Idempotency-Key': idempotencyKey }),
    body: JSON.stringify({ ticketTierId, quantity })
  });
}

export function fetchOrder(orderNumber: string): Promise<OrderResponse> {
  return requestJson<OrderResponse>(`/api/orders/${orderNumber}`, {
    headers: userHeaders()
  });
}

export function fetchMyOrders(): Promise<OrderSummary[]> {
  return requestJson<OrderSummary[]>('/api/orders/me', {
    headers: userHeaders()
  });
}

export function cancelOrder(orderNumber: string): Promise<OrderResponse> {
  return requestJson<OrderResponse>(`/api/orders/${orderNumber}/cancel`, {
    method: 'POST',
    headers: userHeaders()
  });
}

export function createPaymentSession(orderNumber: string, idempotencyKey: string): Promise<PaymentSessionResponse> {
  return requestJson<PaymentSessionResponse>(`/api/payments/orders/${orderNumber}`, {
    method: 'POST',
    headers: userHeaders({ 'Idempotency-Key': idempotencyKey })
  });
}

export function fetchPayment(paymentTransactionId: string): Promise<PaymentQueryResponse> {
  return requestJson<PaymentQueryResponse>(`/api/payments/${paymentTransactionId}`, {
    headers: userHeaders()
  });
}

export function simulatePaymentSuccess(paymentTransactionId: string): Promise<PaymentCallbackResponse> {
  return requestJson<PaymentCallbackResponse>(`/api/payment-simulator/${paymentTransactionId}/success`, {
    method: 'POST',
    headers: jsonHeaders()
  });
}

export function simulatePaymentFailure(paymentTransactionId: string): Promise<PaymentCallbackResponse> {
  return requestJson<PaymentCallbackResponse>(`/api/payment-simulator/${paymentTransactionId}/failure`, {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify({ reason: 'SIMULATED_PAYMENT_DECLINED' })
  });
}

export type LoadState = 'idle' | 'loading' | 'loaded' | 'empty' | 'error';

export type TabKey = 'purchase' | 'dashboard' | 'how';

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

export type DemoProfile = {
  enabled: boolean;
  profile: string;
  eventSlug: string;
};

export type DemoDashboard = {
  event: {
    id: number;
    slug: string;
    name: string;
    status: string;
  };
  inventory: {
    totalStock: number;
    availableStock: number;
    reservedStock: number;
    soldStock: number;
    calculatedTotal: number;
    inventoryConsistent: boolean;
  };
  ticketTiers: Array<{
    id: number;
    code: string;
    name: string;
    totalStock: number;
    availableStock: number;
    reservedStock: number;
    soldStock: number;
    calculatedTotal: number;
    inventoryConsistent: boolean;
  }>;
  orders: {
    total: number;
    pendingPayment: number;
    paid: number;
    cancelled: number;
    refunded: number;
  };
  payments: {
    pending: number;
    success: number;
    failed: number;
  };
  recentOrders: Array<{
    orderNumber: string;
    ticketTierCode: string;
    ticketTierName: string;
    quantity: number;
    status: string;
    totalAmount: number;
    latestPaymentTransactionId: string | null;
    latestPaymentStatus: string | null;
    createdAt: string;
    expiresAt: string;
    paidAt: string | null;
    cancelledAt: string | null;
  }>;
  generatedAt: string;
};

export type DemoResetResponse = {
  eventSlug: string;
  deletedOrders: number;
  deletedPayments: number;
  availableStock: number;
  reservedStock: number;
  soldStock: number;
  inventoryConsistent: boolean;
  resetAt: string;
};

export type TimelineEntry = {
  id: string;
  at: string;
  message: string;
};

export type ReservationIntent = {
  ticketTierId: number;
  quantity: number;
  idempotencyKey: string;
};

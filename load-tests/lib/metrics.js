import { Counter, Rate, Trend } from 'k6/metrics';

export const orderCreated = new Counter('order_created');
export const orderReplayed = new Counter('order_replayed');
export const outOfStock = new Counter('out_of_stock');
export const unexpectedError = new Counter('unexpected_error');
export const oversellDetected = new Counter('oversell_detected');
export const inventoryInconsistent = new Counter('inventory_inconsistent');
export const duplicateOrderDetected = new Counter('duplicate_order_detected');
export const duplicatePaymentProcessingDetected = new Counter('duplicate_payment_processing_detected');
export const businessSuccessRate = new Rate('business_success_rate');
export const orderCreateDuration = new Trend('order_create_duration');
export const paymentCallbackDuration = new Trend('payment_callback_duration');
export const paymentSessionDuration = new Trend('payment_session_duration');
export const totalJourneyDuration = new Trend('total_journey_duration');

export const correctnessThresholds = {
  oversell_detected: ['count==0'],
  inventory_inconsistent: ['count==0'],
  duplicate_order_detected: ['count==0'],
  duplicate_payment_processing_detected: ['count==0'],
  unexpected_error: ['count==0']
};

export const provisionalPerformanceThresholds = {
  http_req_duration: ['p(95)<3000', 'p(99)<5000']
};

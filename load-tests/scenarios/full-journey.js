import { config, loadUserEmail } from '../config/environments.js';
import { createOrder, createPayment, eventBySlug, getOrder, resetLoadTest, simulatePaymentSuccess, uuid } from '../lib/api.js';
import { expectStatus, parseJson, recordOrderCreate } from '../lib/assertions.js';
import { orderCreateDuration, paymentCallbackDuration, paymentSessionDuration, provisionalPerformanceThresholds, totalJourneyDuration, unexpectedError } from '../lib/metrics.js';
import { summaryOutputs } from '../lib/summary.js';

export const options = {
  stages: [
    { duration: '10s', target: Math.min(config.vus, 20) },
    { duration: config.duration, target: Math.min(config.vus, 20) },
    { duration: '10s', target: 0 }
  ],
  thresholds: {
    ...provisionalPerformanceThresholds,
    unexpected_error: ['count==0']
  }
};

export function setup() {
  resetLoadTest({ totalStock: Number(__ENV.TOTAL_STOCK || '10000'), userCount: Math.max(config.userCount, config.vus * 20) });
  const event = parseJson(eventBySlug(config.eventSlug), 'event by slug');
  return { ticketTierId: event.ticketTiers.find((tier) => tier.code === config.ticketCode)?.id };
}

export default function (fixture) {
  const journeyStart = Date.now();
  const user = loadUserEmail((__VU + __ITER) % config.userCount + 1);
  const orderStart = Date.now();
  const orderResponse = createOrder(user, fixture.ticketTierId, uuid('journey-order'));
  orderCreateDuration.add(Date.now() - orderStart);
  recordOrderCreate(orderResponse);
  if (orderResponse.status !== 201) {
    unexpectedError.add(1);
    return;
  }
  const order = parseJson(orderResponse, 'journey order');
  const paymentStart = Date.now();
  const paymentResponse = createPayment(user, order.orderNumber, uuid('journey-payment'));
  paymentSessionDuration.add(Date.now() - paymentStart);
  expectStatus(paymentResponse, [201, 200], 'journey payment');
  const payment = parseJson(paymentResponse, 'journey payment');
  const callbackStart = Date.now();
  expectStatus(simulatePaymentSuccess(payment.paymentTransactionId), [200], 'journey payment success');
  paymentCallbackDuration.add(Date.now() - callbackStart);
  const paidOrder = parseJson(getOrder(user, order.orderNumber), 'journey paid order');
  if (paidOrder.status !== 'PAID') {
    unexpectedError.add(1);
  }
  totalJourneyDuration.add(Date.now() - journeyStart);
}

export function handleSummary(data) {
  return summaryOutputs(data, 'full-journey');
}

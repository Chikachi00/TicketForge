import { config, loadUserEmail } from '../config/environments.js';
import {
  createOrder,
  createPayment,
  eventBySlug,
  events,
  getLoadTestState,
  getOrder,
  health,
  resetLoadTest,
  simulatePaymentSuccess,
  uuid
} from '../lib/api.js';
import { assertState, expectStatus, parseJson, recordOrderCreate } from '../lib/assertions.js';
import { correctnessThresholds, provisionalPerformanceThresholds } from '../lib/metrics.js';
import { summaryOutputs } from '../lib/summary.js';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { ...correctnessThresholds, ...provisionalPerformanceThresholds }
};

export function setup() {
  return resetLoadTest({ totalStock: Number(__ENV.TOTAL_STOCK || 5), userCount: Number(__ENV.USER_COUNT || 5) });
}

export default function (fixture) {
  expectStatus(health(), [200], 'health');
  expectStatus(events(), [200], 'events');
  const eventResponse = eventBySlug(config.eventSlug);
  expectStatus(eventResponse, [200], 'event by slug');
  const event = parseJson(eventResponse, 'event by slug');
  const tierId = event.ticketTiers.find((tier) => tier.code === config.ticketCode)?.id || fixture.ticketTierId;
  const user = loadUserEmail(1);

  const orderResponse = createOrder(user, tierId, uuid('order'));
  recordOrderCreate(orderResponse);
  expectStatus(orderResponse, [201], 'create order');
  const order = parseJson(orderResponse, 'create order');

  expectStatus(getOrder(user, order.orderNumber), [200], 'get order');

  const paymentResponse = createPayment(user, order.orderNumber, uuid('payment'));
  expectStatus(paymentResponse, [201, 200], 'create payment');
  const payment = parseJson(paymentResponse, 'create payment');

  expectStatus(simulatePaymentSuccess(payment.paymentTransactionId), [200], 'simulate payment success');
  const paidOrderResponse = getOrder(user, order.orderNumber);
  expectStatus(paidOrderResponse, [200], 'get paid order');
  const paidOrder = parseJson(paidOrderResponse, 'get paid order');
  if (paidOrder.status !== 'PAID') {
    throw new Error(`Expected PAID order, got ${paidOrder.status}`);
  }

  const state = getLoadTestState(config.eventSlug);
  assertState(state, Number(__ENV.TOTAL_STOCK || 5));
}

export function handleSummary(data) {
  return summaryOutputs(data, 'smoke');
}

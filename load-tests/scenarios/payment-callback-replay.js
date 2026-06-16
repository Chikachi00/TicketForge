import http from 'k6/http';
import { config, loadUserEmail } from '../config/environments.js';
import { createOrder, createPayment, eventBySlug, getLoadTestState, headers, paymentCallback, paymentCallbackSignature, resetLoadTest, uuid } from '../lib/api.js';
import { assertSinglePaymentSuccess, assertState, expectStatus, parseJson, recordOrderCreate } from '../lib/assertions.js';
import { correctnessThresholds, paymentCallbackDuration, provisionalPerformanceThresholds } from '../lib/metrics.js';
import { summaryOutputs } from '../lib/summary.js';

const REQUESTS = Number(__ENV.CALLBACK_REPLAY_REQUESTS || '20');

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { ...correctnessThresholds, ...provisionalPerformanceThresholds }
};

export function setup() {
  resetLoadTest({ totalStock: 1, userCount: 1 });
  const event = parseJson(eventBySlug(config.eventSlug), 'event by slug');
  return { ticketTierId: event.ticketTiers.find((tier) => tier.code === config.ticketCode)?.id };
}

export default function (fixture) {
  const user = loadUserEmail(1);
  const orderResponse = createOrder(user, fixture.ticketTierId, uuid('callback-order'));
  recordOrderCreate(orderResponse);
  const order = parseJson(orderResponse, 'create order');
  const payment = parseJson(createPayment(user, order.orderNumber, uuid('callback-payment')), 'create payment');
  const occurredAt = new Date().toISOString();
  const request = {
    providerEventId: uuid('EVT'),
    paymentTransactionId: payment.paymentTransactionId,
    orderNumber: order.orderNumber,
    status: 'SUCCESS',
    amount: payment.amount,
    currency: payment.currency,
    occurredAt
  };
  const first = paymentCallback(request);
  expectStatus(first, [200], 'initial callback');
  const signature = paymentCallbackSignature(request);
  const batch = Array.from({ length: REQUESTS - 1 }, () => ['POST', `${config.baseUrl}/api/payments/callback`, JSON.stringify(request), {
    headers: headers({ 'X-Payment-Signature': signature }),
    tags: { name: 'payment-callback-replay' }
  }]);
  const started = Date.now();
  const responses = http.batch(batch);
  paymentCallbackDuration.add(Date.now() - started);
  responses.forEach((response) => expectStatus(response, [200], 'replayed callback'));
  const state = getLoadTestState(config.eventSlug);
  assertState(state, 1);
  assertSinglePaymentSuccess(state);
}

export function handleSummary(data) {
  return summaryOutputs(data, 'payment-callback-replay');
}

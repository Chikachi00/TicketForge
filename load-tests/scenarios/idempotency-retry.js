import http from 'k6/http';
import { config, loadUserEmail } from '../config/environments.js';
import { eventBySlug, getLoadTestState, orderCreateRequestOptions, resetLoadTest, uuid } from '../lib/api.js';
import { assertSingleOrder, assertState, parseJson, recordOrderCreate } from '../lib/assertions.js';
import { correctnessThresholds, provisionalPerformanceThresholds } from '../lib/metrics.js';
import { summaryOutputs } from '../lib/summary.js';

const REQUESTS = Number(__ENV.IDEMPOTENCY_REQUESTS || '20');

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { ...correctnessThresholds, ...provisionalPerformanceThresholds }
};

export function setup() {
  resetLoadTest({ totalStock: 10, userCount: 1 });
  const event = parseJson(eventBySlug(config.eventSlug), 'event by slug');
  return { ticketTierId: event.ticketTiers.find((tier) => tier.code === config.ticketCode)?.id };
}

export default function (fixture) {
  const user = loadUserEmail(1);
  const idempotencyKey = uuid('same-key');
  const batch = Array.from({ length: REQUESTS }, () => [
    'POST',
    `${config.baseUrl}/api/orders`,
    JSON.stringify({ ticketTierId: fixture.ticketTierId, quantity: 1 }),
    orderCreateRequestOptions(
      {
        'X-User-Email': user,
        'Idempotency-Key': idempotencyKey
      },
      { name: 'order-create-idempotent' }
    )
  ]);
  const responses = http.batch(batch);
  const orderNumbers = [];
  responses.forEach((response) => {
    recordOrderCreate(response);
    if (response.status === 200 || response.status === 201) {
      orderNumbers.push(parseJson(response, 'idempotent order').orderNumber);
    }
  });
  assertSingleOrder(orderNumbers);
  const state = getLoadTestState(config.eventSlug);
  assertState(state, 10);
  if (state.pendingOrders !== 1 || state.reservedStock !== 1) {
    throw new Error(`Idempotency retry reserved more than once: ${JSON.stringify(state)}`);
  }
}

export function handleSummary(data) {
  return summaryOutputs(data, 'idempotency-retry');
}

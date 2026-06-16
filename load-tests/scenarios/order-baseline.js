import { config, loadUserEmail } from '../config/environments.js';
import { createOrder, eventBySlug, resetLoadTest, uuid } from '../lib/api.js';
import { parseJson, recordOrderCreate } from '../lib/assertions.js';
import { orderCreateDuration, provisionalPerformanceThresholds, unexpectedError } from '../lib/metrics.js';
import { summaryOutputs } from '../lib/summary.js';

export const options = {
  stages: [
    { duration: __ENV.WARMUP_DURATION || '5s', target: 10 },
    { duration: __ENV.RAMP_DURATION || '20s', target: config.vus },
    { duration: config.duration, target: config.vus },
    { duration: __ENV.RAMPDOWN_DURATION || '5s', target: 0 }
  ],
  thresholds: {
    ...provisionalPerformanceThresholds,
    unexpected_error: ['count==0']
  }
};

export function setup() {
  const stock = Number(__ENV.TOTAL_STOCK || '100000');
  const users = Number(__ENV.USER_COUNT || String(Math.max(config.userCount, config.vus * 10)));
  resetLoadTest({ totalStock: stock, userCount: users });
  const event = parseJson(eventBySlug(config.eventSlug), 'event by slug');
  return { ticketTierId: event.ticketTiers.find((tier) => tier.code === config.ticketCode)?.id };
}

export default function (fixture) {
  const started = Date.now();
  const userIndex = ((__VU + __ITER) % config.userCount) + 1;
  const response = createOrder(loadUserEmail(userIndex), fixture.ticketTierId, uuid('baseline'));
  orderCreateDuration.add(Date.now() - started);
  recordOrderCreate(response);
  if (response.status !== 201) {
    unexpectedError.add(1);
  }
}

export function handleSummary(data) {
  return summaryOutputs(data, 'order-baseline');
}

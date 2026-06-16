import { config, loadUserEmail } from '../config/environments.js';
import { createOrder, eventBySlug, getLoadTestState, resetLoadTest, uuid } from '../lib/api.js';
import { assertState, parseJson, recordOrderCreate } from '../lib/assertions.js';
import { correctnessThresholds, provisionalPerformanceThresholds } from '../lib/metrics.js';
import { summaryOutputs } from '../lib/summary.js';

export const options = {
  scenarios: {
    spike: {
      executor: 'shared-iterations',
      vus: config.vus,
      iterations: config.vus,
      maxDuration: __ENV.MAX_DURATION || '1m'
    }
  },
  thresholds: { ...correctnessThresholds, ...provisionalPerformanceThresholds }
};

export function setup() {
  const stock = Number(__ENV.TOTAL_STOCK || '100');
  resetLoadTest({ totalStock: stock, userCount: Math.max(config.userCount, config.vus + 10) });
  const event = parseJson(eventBySlug(config.eventSlug), 'event by slug');
  return {
    totalStock: stock,
    ticketTierId: event.ticketTiers.find((tier) => tier.code === config.ticketCode)?.id
  };
}

export default function (fixture) {
  const userIndex = __ITER + 1;
  const response = createOrder(loadUserEmail(userIndex), fixture.ticketTierId, uuid('spike'));
  recordOrderCreate(response);
}

export function teardown(fixture) {
  const state = getLoadTestState(config.eventSlug);
  assertState(state, fixture.totalStock);
  if (state.pendingOrders !== fixture.totalStock || state.reservedStock !== fixture.totalStock || state.availableStock !== 0) {
    throw new Error(`Unexpected oversell final state: ${JSON.stringify(state)}`);
  }
}

export function handleSummary(data) {
  return summaryOutputs(data, 'oversell-spike');
}

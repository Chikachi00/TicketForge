export const config = {
  baseUrl: __ENV.BASE_URL || 'http://127.0.0.1:8080',
  loadTestSecret: __ENV.LOAD_TEST_SECRET || 'ticketforge-local-loadtest-secret',
  paymentCallbackSecret: __ENV.PAYMENT_CALLBACK_SECRET || 'ticketforge-local-dev-secret',
  eventSlug: __ENV.EVENT_SLUG || 'ticketforge-load-test-live',
  ticketCode: __ENV.TICKET_CODE || 'LOAD',
  totalStock: Number(__ENV.TOTAL_STOCK || '100'),
  userCount: Number(__ENV.USER_COUNT || '1000'),
  vus: Number(__ENV.VUS || '50'),
  duration: __ENV.DURATION || '30s',
  targetRps: Number(__ENV.TARGET_RPS || '50'),
  maxVus: Number(__ENV.MAX_VUS || '100')
};

export function loadUserEmail(index) {
  const safeIndex = Math.max(1, Number(index) || 1);
  return `loadtest-user-${String(safeIndex).padStart(6, '0')}@ticketforge.local`;
}

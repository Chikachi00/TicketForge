import crypto from 'k6/crypto';
import http from 'k6/http';
import { config } from '../config/environments.js';
import { expectStatus, parseJson } from './assertions.js';

export function headers(extra = {}) {
  return {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    ...extra
  };
}

export function loadTestHeaders() {
  return headers({ 'X-Load-Test-Secret': config.loadTestSecret });
}

export function resetLoadTest(options = {}) {
  const body = {
    eventSlug: options.eventSlug || config.eventSlug,
    ticketCode: options.ticketCode || config.ticketCode,
    totalStock: Number(options.totalStock || config.totalStock),
    userCount: Number(options.userCount || config.userCount)
  };
  const response = http.post(`${config.baseUrl}/api/load-test/reset`, JSON.stringify(body), {
    headers: loadTestHeaders(),
    tags: { name: 'load-test-reset' }
  });
  expectStatus(response, [200], 'load-test reset');
  return parseJson(response, 'load-test reset');
}

export function getLoadTestState(eventSlug = config.eventSlug) {
  const response = http.get(`${config.baseUrl}/api/load-test/state?eventSlug=${encodeURIComponent(eventSlug)}`, {
    headers: loadTestHeaders(),
    tags: { name: 'load-test-state' }
  });
  expectStatus(response, [200], 'load-test state');
  return parseJson(response, 'load-test state');
}

export function getProfile() {
  return http.get(`${config.baseUrl}/api/load-test/profile`, {
    headers: loadTestHeaders(),
    tags: { name: 'load-test-profile' }
  });
}

export function health() {
  return http.get(`${config.baseUrl}/actuator/health`, { tags: { name: 'health' } });
}

export function events() {
  return http.get(`${config.baseUrl}/api/events`, { tags: { name: 'events' } });
}

export function eventBySlug(slug = config.eventSlug) {
  return http.get(`${config.baseUrl}/api/events/slug/${slug}`, { tags: { name: 'event-by-slug' } });
}

export function createOrder(userEmail, ticketTierId, idempotencyKey, quantity = 1) {
  return http.post(`${config.baseUrl}/api/orders`, JSON.stringify({ ticketTierId, quantity }), {
    headers: headers({
      'X-User-Email': userEmail,
      'Idempotency-Key': idempotencyKey
    }),
    tags: { name: 'order-create' }
  });
}

export function getOrder(userEmail, orderNumber) {
  return http.get(`${config.baseUrl}/api/orders/${orderNumber}`, {
    headers: headers({ 'X-User-Email': userEmail }),
    tags: { name: 'order-get' }
  });
}

export function createPayment(userEmail, orderNumber, idempotencyKey) {
  return http.post(`${config.baseUrl}/api/payments/orders/${orderNumber}`, null, {
    headers: headers({
      'X-User-Email': userEmail,
      'Idempotency-Key': idempotencyKey
    }),
    tags: { name: 'payment-create' }
  });
}

export function simulatePaymentSuccess(paymentTransactionId) {
  return http.post(`${config.baseUrl}/api/payment-simulator/${paymentTransactionId}/success`, null, {
    headers: headers(),
    tags: { name: 'payment-simulator-success' }
  });
}

export function paymentCallback(request) {
  const signature = paymentCallbackSignature(request);
  return http.post(`${config.baseUrl}/api/payments/callback`, JSON.stringify(request), {
    headers: headers({ 'X-Payment-Signature': signature }),
    tags: { name: 'payment-callback' }
  });
}

export function paymentCallbackSignature(request) {
  const signingString = [
    request.providerEventId,
    request.paymentTransactionId,
    request.orderNumber,
    request.status,
    Number(request.amount).toFixed(2),
    request.currency,
    request.occurredAt
  ].join('|');
  return crypto.hmac('sha256', config.paymentCallbackSecret, signingString, 'hex');
}

export function uuid(prefix = 'k6') {
  return `${prefix}-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

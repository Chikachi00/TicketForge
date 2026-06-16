import { check, fail } from 'k6';
import {
  businessSuccessRate,
  duplicateOrderDetected,
  duplicatePaymentProcessingDetected,
  inventoryInconsistent,
  orderCreated,
  orderReplayed,
  outOfStock,
  oversellDetected,
  unexpectedError
} from './metrics.js';

export function expectStatus(response, statuses, label) {
  const ok = check(response, {
    [`${label} status ${statuses.join('/')}`]: (res) => statuses.includes(res.status)
  });
  if (!ok) {
    unexpectedError.add(1);
  }
  return ok;
}

export function parseJson(response, label) {
  try {
    return response.json();
  } catch (error) {
    unexpectedError.add(1);
    fail(`${label} did not return JSON`);
  }
}

export function recordOrderCreate(response) {
  if (response.status === 201) {
    orderCreated.add(1);
    businessSuccessRate.add(true);
    return;
  }
  if (response.status === 200) {
    orderReplayed.add(1);
    businessSuccessRate.add(true);
    return;
  }
  if (response.status === 409) {
    const body = parseJson(response, 'order rejection');
    if (body.code === 'OUT_OF_STOCK') {
      outOfStock.add(1);
      businessSuccessRate.add(true);
      return;
    }
  }
  unexpectedError.add(1);
  businessSuccessRate.add(false);
}

export function assertState(state, expectedTotalStock) {
  if (!state.inventoryConsistent || state.calculatedTotal !== state.totalStock) {
    inventoryInconsistent.add(1);
  }
  if (state.oversellDetected || state.pendingOrders + state.paidOrders > state.totalStock) {
    oversellDetected.add(1);
  }
  if (state.negativeInventoryDetected || state.availableStock < 0 || state.reservedStock < 0 || state.soldStock < 0) {
    inventoryInconsistent.add(1);
  }
  if (expectedTotalStock !== undefined && state.totalStock !== expectedTotalStock) {
    inventoryInconsistent.add(1);
  }
}

export function assertSingleOrder(orderNumbers) {
  const unique = new Set(orderNumbers.filter(Boolean));
  if (unique.size > 1) {
    duplicateOrderDetected.add(1);
  }
}

export function assertSinglePaymentSuccess(state) {
  if (state.paymentSuccessCount !== 1 || state.soldStock !== 1) {
    duplicatePaymentProcessingDetected.add(1);
  }
}

import { formatCurrency, formatDate } from '../format';
import { PaymentQueryResponse, PaymentSessionResponse } from '../types';
import { EmptyState } from './EmptyState';

type PaymentPanelProps = {
  paymentSession: PaymentSessionResponse | null;
  settledPayment: PaymentQueryResponse | null;
  paymentMessage: string | null;
  busy: boolean;
  onCreatePayment: () => void;
  onSimulateSuccess: () => void;
  onSimulateFailure: () => void;
};

export function PaymentPanel({
  paymentSession,
  settledPayment,
  paymentMessage,
  busy,
  onCreatePayment,
  onSimulateSuccess,
  onSimulateFailure
}: PaymentPanelProps) {
  return (
    <section className="payment-box">
      <div className="section-heading compact-heading">
        <h3>Payment</h3>
        <span>{paymentSession?.status ?? settledPayment?.status ?? 'none'}</span>
      </div>
      {!paymentSession && !settledPayment && <EmptyState title="No payment session" />}
      <button className="primary-action full-width-action" type="button" onClick={onCreatePayment} disabled={busy}>
        {busy && !paymentSession ? 'Creating payment session...' : 'Create payment session'}
      </button>
      {paymentSession && (
        <>
          <dl>
            <div>
              <dt>Transaction</dt>
              <dd>{paymentSession.paymentTransactionId}</dd>
            </div>
            <div>
              <dt>Amount</dt>
              <dd>{formatCurrency(paymentSession.amount)}</dd>
            </div>
            <div>
              <dt>Created</dt>
              <dd>{formatDate(paymentSession.createdAt)}</dd>
            </div>
          </dl>
          <div className="payment-actions">
            <button className="primary-action" type="button" onClick={onSimulateSuccess} disabled={busy}>
              Simulate payment success
            </button>
            <button className="secondary-action" type="button" onClick={onSimulateFailure} disabled={busy}>
              Simulate payment failure
            </button>
          </div>
        </>
      )}
      {paymentMessage && <p className="state">{paymentMessage}</p>}
      {settledPayment?.processedAt && (
        <p className="state">
          {settledPayment.status} via {settledPayment.paymentTransactionId} at {formatDate(settledPayment.processedAt)}
        </p>
      )}
    </section>
  );
}

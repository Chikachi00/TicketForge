import { formatCurrency } from '../format';
import { TicketTierDetail } from '../types';

type TicketTierCardProps = {
  tier: TicketTierDetail;
  quantity: number;
  submitting: boolean;
  onQuantityChange: (quantity: number) => void;
  onReserve: () => void;
};

export function TicketTierCard({ tier, quantity, submitting, onQuantityChange, onReserve }: TicketTierCardProps) {
  return (
    <article className="tier-row">
      <div>
        <span className="tier-code">{tier.code}</span>
        <h3>{tier.name}</h3>
      </div>
      <div className="tier-metrics">
        <span>{formatCurrency(tier.price)}</span>
        <strong>{tier.availableStock.toLocaleString()} left</strong>
      </div>
      <label>
        Qty
        <select
          value={quantity}
          onChange={(event) => onQuantityChange(Number(event.target.value))}
          disabled={submitting}
        >
          {[1, 2, 3, 4, 5].map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>
      <button
        className="primary-action"
        type="button"
        onClick={onReserve}
        disabled={submitting || tier.availableStock <= 0}
      >
        {submitting ? 'Processing...' : 'Reserve tickets'}
      </button>
    </article>
  );
}

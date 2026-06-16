import { formatDate } from '../format';
import { EventDetail as EventDetailType, LoadState } from '../types';
import { EmptyState } from './EmptyState';
import { StatusBadge } from './StatusBadge';
import { TicketTierCard } from './TicketTierCard';

type EventDetailProps = {
  event: EventDetailType | null;
  state: LoadState;
  error: string | null;
  quantities: Record<number, number>;
  submittingTierId: number | null;
  onQuantityChange: (tierId: number, quantity: number) => void;
  onReserve: (tierId: number) => void;
};

export function EventDetail({
  event,
  state,
  error,
  quantities,
  submittingTierId,
  onQuantityChange,
  onReserve
}: EventDetailProps) {
  if (state === 'loading') return <section className="panel detail-panel"><p className="state">Loading ticket tiers...</p></section>;
  if (state === 'error') return <section className="panel detail-panel"><p className="state error">{error}</p></section>;
  if (!event) return <section className="panel detail-panel"><EmptyState title="No event selected" /></section>;

  return (
    <section className="panel detail-panel" aria-live="polite">
      <div className="event-hero">
        <StatusBadge status={event.status} />
        <h2>{event.name}</h2>
        <p>{event.description}</p>
        <div className="metadata-grid">
          <div>
            <span>Venue</span>
            <strong>{event.venue}</strong>
          </div>
          <div>
            <span>Performance</span>
            <strong>{formatDate(event.performanceAt)}</strong>
          </div>
          <div>
            <span>Sales start</span>
            <strong>{formatDate(event.salesStartAt)}</strong>
          </div>
        </div>
      </div>
      <div className="tiers">
        {event.ticketTiers.map((tier) => (
          <TicketTierCard
            key={tier.id}
            tier={tier}
            quantity={quantities[tier.id] ?? 1}
            submitting={submittingTierId === tier.id}
            onQuantityChange={(quantity) => onQuantityChange(tier.id, quantity)}
            onReserve={() => onReserve(tier.id)}
          />
        ))}
      </div>
    </section>
  );
}

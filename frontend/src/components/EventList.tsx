import { formatDate } from '../format';
import { EventSummary, LoadState } from '../types';
import { EmptyState } from './EmptyState';
import { StatusBadge } from './StatusBadge';

type EventListProps = {
  events: EventSummary[];
  selectedId: number | null;
  state: LoadState;
  error: string | null;
  onSelect: (eventId: number) => void;
};

export function EventList({ events, selectedId, state, error, onSelect }: EventListProps) {
  return (
    <aside className="panel event-list" aria-label="Events">
      <div className="section-heading">
        <h2>Events</h2>
        <span>{events.length}</span>
      </div>
      {state === 'loading' && <p className="state">Loading events...</p>}
      {state === 'empty' && <EmptyState title="No events" detail="Flyway demo data has not been loaded yet." />}
      {state === 'error' && <p className="state error">{error}</p>}
      <div className="event-stack">
        {events.map((event) => (
          <button
            className={event.id === selectedId ? 'event-card active' : 'event-card'}
            key={event.id}
            onClick={() => onSelect(event.id)}
            type="button"
          >
            <StatusBadge status={event.status} />
            <strong>{event.name}</strong>
            <span>{event.venue}</span>
            <span>{formatDate(event.performanceAt)}</span>
          </button>
        ))}
      </div>
    </aside>
  );
}

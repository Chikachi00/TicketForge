import { useEffect, useMemo, useState } from 'react';
import { EventDetail, EventSummary, fetchEvent, fetchEvents } from './api';
import './App.css';

type LoadState = 'idle' | 'loading' | 'loaded' | 'empty' | 'error';

const currencyFormatter = new Intl.NumberFormat('ja-JP', {
  style: 'currency',
  currency: 'JPY',
  maximumFractionDigits: 0
});

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short'
});

function formatDate(value: string) {
  return dateFormatter.format(new Date(value));
}

function App() {
  const [events, setEvents] = useState<EventSummary[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<EventDetail | null>(null);
  const [listState, setListState] = useState<LoadState>('idle');
  const [detailState, setDetailState] = useState<LoadState>('idle');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setListState('loading');
    fetchEvents()
      .then((data) => {
        if (!active) return;
        setEvents(data);
        setSelectedId(data[0]?.id ?? null);
        setListState(data.length === 0 ? 'empty' : 'loaded');
      })
      .catch(() => {
        if (!active) return;
        setError('Unable to load events from the backend.');
        setListState('error');
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (selectedId === null) {
      setSelectedEvent(null);
      return;
    }

    let active = true;
    setDetailState('loading');
    fetchEvent(selectedId)
      .then((data) => {
        if (!active) return;
        setSelectedEvent(data);
        setDetailState('loaded');
      })
      .catch(() => {
        if (!active) return;
        setError('Unable to load ticket tiers for the selected event.');
        setDetailState('error');
      });
    return () => {
      active = false;
    };
  }, [selectedId]);

  const totalAvailable = useMemo(
    () => selectedEvent?.ticketTiers.reduce((sum, tier) => sum + tier.availableStock, 0) ?? 0,
    [selectedEvent]
  );

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">TicketForge</p>
          <h1>TicketForge</h1>
          <p className="subtitle">高并发票务系统实验项目</p>
        </div>
        <span className="phase-pill">Phase 1</span>
      </header>

      <section className="content-grid">
        <aside className="event-list" aria-label="Events">
          <div className="section-heading">
            <h2>Events</h2>
            <span>{events.length}</span>
          </div>

          {listState === 'loading' && <p className="state">Loading events...</p>}
          {listState === 'empty' && <p className="state">No events are available.</p>}
          {listState === 'error' && <p className="state error">{error}</p>}

          <div className="event-stack">
            {events.map((event) => (
              <button
                className={event.id === selectedId ? 'event-card active' : 'event-card'}
                key={event.id}
                onClick={() => setSelectedId(event.id)}
                type="button"
              >
                <span className="status">{event.status}</span>
                <strong>{event.name}</strong>
                <span>{event.venue}</span>
                <span>{formatDate(event.performanceAt)}</span>
              </button>
            ))}
          </div>
        </aside>

        <section className="detail-panel" aria-live="polite">
          {detailState === 'loading' && <p className="state">Loading ticket tiers...</p>}
          {detailState === 'error' && <p className="state error">{error}</p>}

          {detailState === 'loaded' && selectedEvent && (
            <>
              <div className="event-hero">
                <span className="status">{selectedEvent.status}</span>
                <h2>{selectedEvent.name}</h2>
                <p>{selectedEvent.description}</p>
                <div className="metadata-grid">
                  <div>
                    <span>Venue</span>
                    <strong>{selectedEvent.venue}</strong>
                  </div>
                  <div>
                    <span>Performance</span>
                    <strong>{formatDate(selectedEvent.performanceAt)}</strong>
                  </div>
                  <div>
                    <span>Sales start</span>
                    <strong>{formatDate(selectedEvent.salesStartAt)}</strong>
                  </div>
                  <div>
                    <span>Available</span>
                    <strong>{totalAvailable.toLocaleString()}</strong>
                  </div>
                </div>
              </div>

              <div className="tiers">
                {selectedEvent.ticketTiers.map((tier) => (
                  <article className="tier-row" key={tier.id}>
                    <div>
                      <span className="tier-code">{tier.code}</span>
                      <h3>{tier.name}</h3>
                    </div>
                    <div className="tier-metrics">
                      <span>{currencyFormatter.format(tier.price)}</span>
                      <strong>{tier.availableStock.toLocaleString()} left</strong>
                    </div>
                  </article>
                ))}
              </div>
            </>
          )}
        </section>
      </section>
    </main>
  );
}

export default App;


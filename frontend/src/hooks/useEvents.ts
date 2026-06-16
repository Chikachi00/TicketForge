import { useCallback, useEffect, useState } from 'react';
import { fetchEvent, fetchEvents } from '../api';
import { EventDetail, EventSummary, LoadState } from '../types';

export function useEvents() {
  const [events, setEvents] = useState<EventSummary[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<EventDetail | null>(null);
  const [listState, setListState] = useState<LoadState>('idle');
  const [detailState, setDetailState] = useState<LoadState>('idle');
  const [error, setError] = useState<string | null>(null);

  const refreshEvents = useCallback(() => {
    setListState('loading');
    return fetchEvents()
      .then((data) => {
        setEvents(data);
        setSelectedId((current) => current ?? data[0]?.id ?? null);
        setListState(data.length === 0 ? 'empty' : 'loaded');
        return data;
      })
      .catch((exception: Error) => {
        setError(exception.message || 'Unable to load events from the backend.');
        setListState('error');
        throw exception;
      });
  }, []);

  const refreshSelectedEvent = useCallback(() => {
    if (selectedId === null) return Promise.resolve(null);
    setDetailState('loading');
    return fetchEvent(selectedId)
      .then((data) => {
        setSelectedEvent(data);
        setDetailState('loaded');
        return data;
      })
      .catch((exception: Error) => {
        setError(exception.message || 'Unable to load ticket tiers for the selected event.');
        setDetailState('error');
        throw exception;
      });
  }, [selectedId]);

  useEffect(() => {
    let active = true;
    refreshEvents().catch(() => undefined);
    return () => {
      active = false;
      void active;
    };
  }, [refreshEvents]);

  useEffect(() => {
    if (selectedId === null) {
      setSelectedEvent(null);
      return;
    }
    refreshSelectedEvent().catch(() => undefined);
  }, [refreshSelectedEvent, selectedId]);

  return {
    events,
    selectedId,
    selectedEvent,
    listState,
    detailState,
    error,
    setSelectedId,
    refreshEvents,
    refreshSelectedEvent
  };
}

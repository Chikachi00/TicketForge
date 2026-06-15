export type TicketTierSummary = {
  id: number;
  code: string;
  name: string;
  price: number;
  totalStock: number;
  availableStock: number;
};

export type TicketTierDetail = TicketTierSummary & {
  reservedStock: number;
  soldStock: number;
};

export type EventSummary = {
  id: number;
  slug: string;
  name: string;
  venue: string;
  description: string | null;
  performanceAt: string;
  salesStartAt: string;
  status: string;
  ticketTiers: TicketTierSummary[];
};

export type EventDetail = Omit<EventSummary, 'ticketTiers'> & {
  ticketTiers: TicketTierDetail[];
};

async function requestJson<T>(path: string): Promise<T> {
  const response = await fetch(path, {
    headers: {
      Accept: 'application/json'
    }
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export function fetchEvents(): Promise<EventSummary[]> {
  return requestJson<EventSummary[]>('/api/events');
}

export function fetchEvent(eventId: number): Promise<EventDetail> {
  return requestJson<EventDetail>(`/api/events/${eventId}`);
}


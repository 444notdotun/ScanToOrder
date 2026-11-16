import { useQuery } from '@tanstack/react-query';
import { dataOf, http } from '@/lib/http';

export type EventStatus = 'DRAFT' | 'ACTIVE' | 'ENDED';
export type SeatingMode = 'FREE' | 'ASSIGNED';

export interface PlannerEvent {
  id: string;
  name: string;
  venue?: string;
  eventCode: string;
  startsAt: string;
  endsAt: string;
  seatingMode: SeatingMode;
  platesPerGuest?: number | null;
  expectedGuests: number;
  paidGuests: number;
  guestsOnList: number;
  status: EventStatus;
}

export interface EventForm {
  name: string;
  venue: string;
  startsAt: string;
  endsAt: string;
  seatingMode: SeatingMode;
  platesPerGuest: number | null;
  expectedGuests: number;
}

export interface EventPrice {
  guests: number;
  feePerGuestKobo: number;
  amountKobo: number;
}

export interface EventPlate {
  id: string;
  name: string;
  description?: string;
  available: boolean;
}

export interface EventTable {
  tableId: string;
  tableNumber: string;
  displayNumber?: number;
  capacity: number;
}

export interface EventGuest {
  id: string;
  name: string;
  phone?: string;
  email?: string;
  code: string;
  plateLimit?: number | null;
  platesUsed: number;
  tableNumber?: number;
  seatPosition?: number;
  seatedAt?: string;
}

export type CoverageState = 'NOT_ARRIVED' | 'SEATED_NOT_ORDERED' | 'WAITING_FOR_PLATE' | 'EATEN';

export interface Coverage {
  onList: number;
  notArrived: number;
  seatedNotOrdered: number;
  waitingForPlate: number;
  eaten: number;
  guests: Array<{
    guestId: string;
    name: string;
    state: CoverageState;
    tableNumber?: number;
    seatPosition?: number;
    platesUsed: number;
    plateLimit?: number | null;
  }>;
}

interface Done {
  message?: string;
}

// "2026-12-12T18:00" from a date-time box, as the backend wants it.
const body = (form: EventForm) => ({
  ...form,
  venue: form.venue.trim(),
  name: form.name.trim(),
  startsAt: form.startsAt.length === 16 ? `${form.startsAt}:00` : form.startsAt,
  endsAt: form.endsAt.length === 16 ? `${form.endsAt}:00` : form.endsAt,
  platesPerGuest: form.platesPerGuest ?? undefined,
});

export const plannerApi = {
  events: async () => dataOf<PlannerEvent[]>(await http.get('/api/v1/events')),
  event: async (id: string) => dataOf<PlannerEvent>(await http.get(`/api/v1/events/${id}`)),
  create: async (form: EventForm) => dataOf<PlannerEvent>(await http.post('/api/v1/events', body(form))),
  update: async (id: string, form: EventForm) => dataOf<PlannerEvent>(await http.put(`/api/v1/events/${id}`, body(form))),
  end: async (id: string) => dataOf<PlannerEvent>(await http.post(`/api/v1/events/${id}/end`)),
  price: async (id: string, guests: number) => dataOf<EventPrice>(await http.get(`/api/v1/events/${id}/price`, { params: { guests } })),
  pay: async (id: string, guests: number) => dataOf<{ authorizationUrl: string; reference: string }>(await http.post(`/api/v1/events/${id}/pay`, { guests })),
  verify: async (reference: string) => dataOf<{ paymentStatus: string }>(await http.get(`/api/v1/payments/verify/${reference}`)),

  plates: async (id: string) => dataOf<EventPlate[]>(await http.get(`/api/v1/events/${id}/plates`)),
  addPlate: async (id: string, name: string, description: string) => dataOf<Done>(await http.post(`/api/v1/events/${id}/plates`, { name: name.trim(), description: description.trim() })),
  setPlate: async (id: string, plate: EventPlate, available: boolean) =>
    dataOf<Done>(await http.put(`/api/v1/events/${id}/plates/${plate.id}`, { name: plate.name, description: plate.description ?? '', available })),
  removePlate: async (id: string, plateId: string) => dataOf<Done>(await http.delete(`/api/v1/events/${id}/plates/${plateId}`)),

  tables: async (id: string) => dataOf<EventTable[]>(await http.get(`/api/v1/events/${id}/tables`)),
  addTable: async (id: string, seatCapacity: number, displayNumber?: number) => dataOf<Done>(await http.post(`/api/v1/events/${id}/tables`, { seatCapacity, displayNumber })),
  removeTable: async (id: string, tableId: string) => dataOf<Done>(await http.delete(`/api/v1/events/${id}/tables/${tableId}`)),

  guests: async (id: string) => dataOf<EventGuest[]>(await http.get(`/api/v1/events/${id}/guests`)),
  addGuest: async (id: string, guest: { name: string; phone?: string; email?: string; tableId?: string }) => dataOf<EventGuest>(await http.post(`/api/v1/events/${id}/guests`, guest)),
  removeGuest: async (id: string, guestId: string) => dataOf<Done>(await http.delete(`/api/v1/events/${id}/guests/${guestId}`)),
  setLimit: async (id: string, guestId: string, limit: number | null) =>
    dataOf<EventGuest>(await http.patch(`/api/v1/events/${id}/guests/${guestId}/limit`, limit === null ? { unlimited: true } : { limit })),
  exportCsv: async (id: string) => (await http.get<Blob>(`/api/v1/events/${id}/guests/export`, { responseType: 'blob' })).data,
  coverage: async (id: string) => dataOf<Coverage>(await http.get(`/api/v1/events/${id}/coverage`)),
};

// Every list refetches when the live stream says something changed; nothing here asks the server on a timer.
export const useEvents = () => useQuery({ queryKey: ['events'], queryFn: plannerApi.events });
export const useEvent = (id: string) => useQuery({ queryKey: ['event', id], queryFn: () => plannerApi.event(id) });
export const useEventPlates = (id: string) => useQuery({ queryKey: ['event-plates', id], queryFn: () => plannerApi.plates(id) });
export const useEventTables = (id: string) => useQuery({ queryKey: ['event-tables', id], queryFn: () => plannerApi.tables(id) });
export const useEventGuests = (id: string) => useQuery({ queryKey: ['event-guests', id], queryFn: () => plannerApi.guests(id) });
export const useCoverage = (id: string) => useQuery({ queryKey: ['event-coverage', id], queryFn: () => plannerApi.coverage(id) });

export function when(iso?: string): string {
  if (!iso) return '';
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('en-NG', { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
}

// "QX7KLM" reads easier aloud as "QX7 KLM".
export function spaced(code: string): string {
  return code.length === 6 ? `${code.slice(0, 3)} ${code.slice(3)}` : code;
}

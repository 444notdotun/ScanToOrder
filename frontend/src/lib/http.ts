import axios, { AxiosError } from 'axios';

const BASE = process.env.NEXT_PUBLIC_API_URL?.replace(/\/+$/, '') || '';

// The client for the Plately screens. It never shows a toast: a screen shows the backend's own words in a
// failure card and keeps what the person typed.
export const http = axios.create({
  baseURL: BASE,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});

export function apiUrl(path: string): string {
  return `${BASE}${path}`;
}

export interface ApiFailure {
  status: number | null;
  title: string;
  message: string;
  // True when the server could not be reached at all.
  offline: boolean;
  // A form's mistakes, one plain sentence per field, so each can sit under its own field.
  fieldErrors?: Record<string, string>;
  // The restaurant the server named when it said the place is closed.
  businessName?: string;
}

const CALM = 'Something went wrong on our side. Try again in a moment.';

// Words that only a developer should ever read: a guest or a staff member is given the calm line instead, and the real
// text goes to the browser console, where it can be found.
const TECHNICAL = /validation failed|exception|constraint|stack ?trace|\bnull\b|undefined|\bat [\w.$]+\(|[a-z]+\.[a-z]+ - |cannot be (blank|null)|\bjson\b|<\/?[a-z]+>|\bSQL\b/i;

// The backend may send HTML-escaped text from older paths; the screens show plain text.
function plain(text: string): string {
  return text
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#x27;/g, "'")
    .replace(/&#x2F;/g, '/');
}

export function failureOf(error: unknown, fallbackTitle = 'That did not work'): ApiFailure {
  const e = error as AxiosError<{ message?: string; error?: string }>;
  if (!e || !e.isAxiosError) {
    return { status: null, title: fallbackTitle, message: 'Something went wrong on our side. Try again in a moment.', offline: false };
  }
  if (!e.response) {
    return {
      status: null,
      title: 'We lost the connection',
      message: 'Check your signal and try again. Nothing was lost.',
      offline: true,
    };
  }
  const data = e.response.data as { message?: string; error?: string; fieldErrors?: Record<string, string>; businessName?: string } | string | undefined;
  const body = typeof data === 'object' && data !== null ? data : undefined;
  const said = body?.message || (typeof data === 'string' ? '' : body?.error) || '';
  let message = said ? plain(said) : CALM;
  if (TECHNICAL.test(message) || message.length > 240 || e.response.status === 500) {
    console.warn('The server said:', e.response.status, said);
    message = CALM;
  }
  return { status: e.response.status, title: fallbackTitle, message, offline: false, fieldErrors: body?.fieldErrors, businessName: body?.businessName };
}

export function dataOf<T>(response: { data: { data: T } }): T {
  return response.data.data;
}

export const SESSION_ENDED_MESSAGES = ['Your session has ended', 'Your visit has ended.'];

export function sessionEnded(failure: ApiFailure): boolean {
  return failure.status === 401 && SESSION_ENDED_MESSAGES.some((text) => failure.message.startsWith(text.replace(/\.$/, '')));
}

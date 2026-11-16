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
}

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
  const data = e.response.data;
  const message = data?.message || data?.error || 'Something went wrong on our side. Try again in a moment.';
  return { status: e.response.status, title: fallbackTitle, message: plain(message), offline: false };
}

export function dataOf<T>(response: { data: { data: T } }): T {
  return response.data.data;
}

export const SESSION_ENDED_MESSAGES = ['Your session has ended', 'Your visit has ended.'];

export function sessionEnded(failure: ApiFailure): boolean {
  return failure.status === 401 && SESSION_ENDED_MESSAGES.some((text) => failure.message.startsWith(text.replace(/\.$/, '')));
}

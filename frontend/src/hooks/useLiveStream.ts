'use client';

import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { apiUrl } from '@/lib/http';

export type StreamKind = 'guest' | 'staff';
export type StreamState = 'connecting' | 'live' | 'lost';

const NOTICES = ['order', 'call', 'seat', 'table', 'menu', 'refund'];
// The server sends a heartbeat every 25 seconds. Three missed beats and the connection is treated as lost.
const SILENCE_MS = 75_000;

/**
 * Keeps one live stream open. A notice only says "something changed": every query is fetched again.
 * When the stream comes back after a break, everything is fetched again too, in case notices were missed.
 */
export function useLiveStream(kind: StreamKind, enabled = true) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<StreamState>('connecting');
  const [caughtUp, setCaughtUp] = useState(false);
  const wasLost = useRef(false);

  useEffect(() => {
    if (!enabled) return;
    let source: EventSource | null = null;
    let silence: ReturnType<typeof setTimeout> | undefined;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let attempts = 0;
    let stopped = false;

    const refetchAll = () => {
      void queryClient.invalidateQueries();
    };

    const lose = () => {
      wasLost.current = true;
      setState('lost');
    };

    const watch = () => {
      clearTimeout(silence);
      silence = setTimeout(() => {
        lose();
        reconnect();
      }, SILENCE_MS);
    };

    const reconnect = () => {
      source?.close();
      clearTimeout(retry);
      attempts += 1;
      // A little longer each time, never more than 15 seconds.
      retry = setTimeout(open, Math.min(15_000, 1000 * attempts));
    };

    const open = () => {
      if (stopped) return;
      source = new EventSource(apiUrl(`/api/v1/stream/${kind}`), { withCredentials: true });
      watch();
      source.addEventListener('connected', () => {
        attempts = 0;
        setState('live');
        watch();
        if (wasLost.current) {
          wasLost.current = false;
          refetchAll();
          setCaughtUp(true);
          setTimeout(() => setCaughtUp(false), 5000);
        }
      });
      source.addEventListener('heartbeat', watch);
      for (const name of NOTICES) {
        source.addEventListener(name, () => {
          watch();
          refetchAll();
        });
      }
      source.onerror = () => {
        lose();
        // The browser retries by itself while the stream is merely interrupted; a closed stream is opened again here.
        if (source?.readyState === EventSource.CLOSED) reconnect();
      };
    };

    const onOffline = () => lose();
    const onOnline = () => {
      reconnect();
    };
    window.addEventListener('offline', onOffline);
    window.addEventListener('online', onOnline);
    open();

    return () => {
      stopped = true;
      window.removeEventListener('offline', onOffline);
      window.removeEventListener('online', onOnline);
      clearTimeout(silence);
      clearTimeout(retry);
      source?.close();
    };
  }, [kind, enabled, queryClient]);

  return { state, lost: state === 'lost', caughtUp };
}

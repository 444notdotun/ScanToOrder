'use client';

import { useEffect, useState } from 'react';

// A clock for "9 minutes left" labels. It only redraws what is already on screen; it never asks the server anything.
export function useNow(everyMs = 30_000) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), everyMs);
    return () => clearInterval(timer);
  }, [everyMs]);
  return now;
}

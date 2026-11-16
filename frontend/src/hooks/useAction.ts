'use client';

import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { failureOf, type ApiFailure } from '@/lib/http';

// "Waiter Ngozi has already delivered ORDER-1." and its kin: the backend names who got there first.
const LOST_RACE = /\balready (started|marked|delivered|taken|sorted|closed)\b/i;

export const FAILURE_ID = 'staff-failure';

// One staff action at a time: the button stays off while it runs, a refusal shows the backend's own words
// (a lost race gets its own title), and every list is fetched again either way so the screen shows what is true.
export function useAction() {
  const queryClient = useQueryClient();
  const [busy, setBusy] = useState<string | null>(null);
  const [failure, setFailure] = useState<ApiFailure | null>(null);

  // A refusal can land below the fold of a tablet: bring it into view.
  useEffect(() => {
    if (failure) document.getElementById(FAILURE_ID)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }, [failure]);

  const run = async (key: string, action: () => Promise<unknown>): Promise<boolean> => {
    setBusy(key);
    setFailure(null);
    let worked = true;
    try {
      await action();
    } catch (error) {
      worked = false;
      const found = failureOf(error, 'That did not go through');
      setFailure(LOST_RACE.test(found.message) ? { ...found, title: 'Someone got there first' } : found);
    } finally {
      await queryClient.invalidateQueries();
      setBusy(null);
    }
    return worked;
  };

  return { busy, failure, run, clear: () => setFailure(null) };
}

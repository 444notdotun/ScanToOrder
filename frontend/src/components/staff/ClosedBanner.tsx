'use client';

import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useConnection } from '@/components/plately/Connection';
import { failureOf } from '@/lib/http';
import { managerApi, useTenant } from '@/lib/manager';

// Shown at the top of every manager page while the place is closed, so nobody wonders why no orders arrive.
export default function ClosedBanner() {
  const { lost } = useConnection();
  const queryClient = useQueryClient();
  const tenant = useTenant();
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  if (!tenant.data || tenant.data.openForOrders) return null;

  const open = async () => {
    setBusy(true);
    setProblem(null);
    try {
      await managerApi.setOpen(true);
    } catch (error) {
      setProblem(failureOf(error, "We couldn't open").message);
    } finally {
      await queryClient.invalidateQueries({ queryKey: ['tenant'] });
      setBusy(false);
    }
  };

  return (
    <div role="status" className="print:hidden mx-auto mt-3 w-full max-w-6xl px-5">
      <div className="flex flex-col gap-3 rounded-[20px] bg-tomato px-5 py-4 text-white shadow-[0_10px_28px_rgba(0,0,0,0.3)] sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="font-heading text-[24px] font-normal leading-tight">You&apos;re closed. Guests can&apos;t order.</p>
          {problem ? <p className="mt-1 text-[15px] font-bold leading-snug">{problem}</p> : null}
        </div>
        <button
          type="button"
          disabled={busy || lost}
          onClick={() => void open()}
          className="min-h-[52px] flex-none rounded-full bg-cream px-6 text-base font-bold text-bottle hover:bg-white"
        >
          {busy ? 'Opening…' : 'Open for orders'}
        </button>
      </div>
    </div>
  );
}

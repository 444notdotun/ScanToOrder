'use client';

import { useEffect, type ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import Ground from '@/components/plately/Ground';
import { Loader } from '@/components/plately/Logo';
import { ConnectionBanner, ConnectionContext } from '@/components/plately/Connection';
import { forgetTable, useSession } from '@/lib/guest';
import { failureOf } from '@/lib/http';
import { useLiveStream } from '@/hooks/useLiveStream';

// Everything a seated guest sees: it needs a live seat, keeps the guest's stream open, and sends the guest
// back to the opening page when the seat is gone (they left, or staff closed the table).
export default function SeatedShell({ children }: { children: ReactNode }) {
  const router = useRouter();
  const session = useSession();
  const stream = useLiveStream('guest', Boolean(session.data));

  const failure = session.isError ? failureOf(session.error) : null;
  const gone = failure?.status === 401;
  useEffect(() => {
    if (gone) {
      forgetTable();
      router.replace(failure?.message.startsWith('Your visit has ended') ? '/?ended=visit' : '/?ended=1');
    }
  }, [gone, failure?.message, router]);

  if (session.isPending || gone) {
    return (
      <Ground>
        <Loader message="Finding your table…" />
      </Ground>
    );
  }
  if (session.isError) {
    return (
      <Ground>
        <ConnectionBanner lost caughtUp={false} />
        <main className="mx-auto flex w-full max-w-md flex-1 flex-col items-center justify-center gap-3 p-8 text-center">
          <p className="font-heading text-3xl leading-tight">We can&apos;t reach your table.</p>
          <p className="text-soft">{failure?.message}</p>
          <button type="button" onClick={() => void session.refetch()} className="mt-2 min-h-[44px] rounded-full bg-cream px-6 font-bold text-bottle">
            Try again
          </button>
        </main>
      </Ground>
    );
  }

  return (
    <ConnectionContext.Provider value={{ lost: stream.lost }}>
      <Ground>
        <ConnectionBanner lost={stream.lost} caughtUp={stream.caughtUp} />
        {children}
      </Ground>
    </ConnectionContext.Provider>
  );
}

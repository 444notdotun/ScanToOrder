'use client';

import Link from 'next/link';
import Ground from '@/components/plately/Ground';
import { Brand } from '@/components/plately/Logo';
import type { ApiFailure } from '@/lib/http';

const BUTTON = 'mt-2 flex min-h-[52px] w-full items-center justify-center rounded-full px-6 text-base font-bold';

// What a guest sees when the table link does not open a table. The server's own words are never shown here: each
// case has its own screen, one next step and one button.
//  - closed: the restaurant is known, and is not taking orders.
//  - lost connection: nothing was lost, try again.
//  - anything else, including a wrong or half-typed link: we can't find this table.
export default function TableProblem({ failure, tableNumber, onRetry }: { failure: ApiFailure; tableNumber: string; onRetry: () => void }) {
  const closed = failure.status === 409 && Boolean(failure.businessName);

  return (
    <Ground>
      <main className="mx-auto flex w-full max-w-md flex-1 animate-rise flex-col gap-4 px-6 pb-8 pt-10 md:max-w-xl">
        <Brand />
        {closed ? (
          <>
            <h1 className="font-heading text-[34px] font-normal leading-tight">{failure.businessName} isn&apos;t taking orders right now.</h1>
            <p className="text-[17px] leading-snug text-soft">
              Table {tableNumber} at {failure.businessName} can&apos;t order from its phone just yet. A waiter can help you.
            </p>
            <button type="button" onClick={onRetry} className={`${BUTTON} bg-cream text-bottle hover:bg-white`}>
              Check again
            </button>
          </>
        ) : failure.offline ? (
          <>
            <h1 className="font-heading text-[34px] font-normal leading-tight">We lost the connection.</h1>
            <p className="text-[17px] leading-snug text-soft">Check your signal and try again. Nothing was lost.</p>
            <button type="button" onClick={onRetry} className={`${BUTTON} bg-cream text-bottle hover:bg-white`}>
              Try again
            </button>
          </>
        ) : (
          <>
            <h1 className="font-heading text-[34px] font-normal leading-tight">We can&apos;t find this table.</h1>
            <p className="text-[17px] leading-snug text-soft">Scan the card on your table again. If it still won&apos;t open, ask a waiter.</p>
            <Link href="/scan" className={`${BUTTON} bg-cream text-bottle hover:bg-white`}>
              Scan the card
            </Link>
          </>
        )}
      </main>
    </Ground>
  );
}

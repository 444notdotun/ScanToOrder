'use client';

import { useState } from 'react';
import Link from 'next/link';
import Ground from '@/components/plately/Ground';
import { Brand } from '@/components/plately/Logo';
import { Handwriting } from '@/components/plately/ui';
import type { ApiFailure } from '@/lib/http';

const BUTTON = 'flex min-h-[52px] w-full items-center justify-center rounded-full px-6 text-base font-bold';
const FILLED = `${BUTTON} bg-tomato text-white hover:bg-[#A23420]`;
const OUTLINE = `${BUTTON} border border-cream/45 text-cream hover:bg-cream/10`;
const HEADING = 'font-heading text-[34px] font-normal leading-[1.08] md:text-[40px]';

// What a guest sees when the table link does not open a table. The server's own words are never shown here: each
// case has its own screen, one next step and one button.
//  - closed: the restaurant is known, and is not taking orders.
//  - lost connection: nothing was lost, try again.
//  - anything else, including a wrong or half-typed link: we can't find this table.
export default function TableProblem({ failure, tableNumber, onRetry }: { failure: ApiFailure; tableNumber: string; onRetry: () => void }) {
  const closed = failure.status === 409 && Boolean(failure.businessName);
  const [help, setHelp] = useState(false);

  return (
    <Ground>
      <main className="mx-auto flex w-full max-w-md flex-1 animate-rise flex-col gap-5 px-6 pb-8 pt-8 md:max-w-xl">
        <Brand />
        {closed ? (
          <>
            <div>
              <p className="mb-2 text-[12px] font-bold uppercase tracking-[0.14em] text-soft">
                {failure.businessName} · Table {tableNumber}
              </p>
              <h1 className={HEADING}>The kitchen isn&apos;t taking orders just now.</h1>
            </div>
            <div className="rounded-3xl bg-paper p-5 text-bottle">
              <p className="text-base font-bold leading-snug">A waiter can tell you when it opens.</p>
              <p className="mt-2 text-base leading-snug text-quiet">Scan the card again once they say the kitchen is open.</p>
            </div>
            <Link href="/scan" className={OUTLINE}>
              Scan the card again
            </Link>
            <Handwriting className="-rotate-[1deg] origin-left">Sit tight, you&apos;re in the right place.</Handwriting>
          </>
        ) : failure.offline ? (
          <>
            <h1 className={HEADING}>We lost the connection.</h1>
            <div className="rounded-3xl bg-paper p-5 text-bottle">
              <p className="text-base leading-snug text-quiet">Check your signal and try again. Nothing was lost.</p>
            </div>
            <button type="button" onClick={onRetry} className={FILLED}>
              Try again
            </button>
          </>
        ) : (
          <>
            <h1 className={HEADING}>Hmm, we can&apos;t find this table.</h1>
            <div className="rounded-3xl bg-paper p-5 text-bottle">
              <p className="text-base font-bold leading-snug">The code on this card didn&apos;t match a table.</p>
              <p className="mt-2 text-base leading-snug text-quiet">Check you scanned the card on your table, or ask a waiter to help.</p>
            </div>
            <div className="flex flex-col gap-3">
              <Link href="/scan" className={FILLED}>
                Scan the card again
              </Link>
              <button type="button" aria-expanded={help} onClick={() => setHelp(!help)} className={OUTLINE}>
                I&apos;m a guest, I need help
              </button>
              {help ? (
                <p role="status" className="animate-fade rounded-2xl bg-cream/10 p-4 text-[15px] leading-snug">
                  Wave to anyone in an apron. They can seat you and show you the card that opens your table.
                </p>
              ) : null}
            </div>
            <Handwriting className="-rotate-[1deg] origin-left">Nothing is wrong on your end.</Handwriting>
          </>
        )}
      </main>
    </Ground>
  );
}

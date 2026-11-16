'use client';

import Link from 'next/link';
import { Suspense, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import Ground from '@/components/plately/Ground';
import { Brand, Watermark } from '@/components/plately/Logo';
import { Button, Sheet } from '@/components/plately/ui';

// The opening page: where someone lands before they have a table.
function Opening() {
  const params = useSearchParams();
  const ended = params.get('ended');
  const [help, setHelp] = useState(false);

  return (
    <Ground>
      <Watermark className="-bottom-[70px] -right-24" />
      <main className="relative mx-auto flex w-full max-w-md flex-1 animate-rise flex-col items-center gap-5 px-7 pb-7 pt-10">
        <Brand />
        <h1 className="mt-3 text-center font-heading text-[40px] font-normal leading-[1.08]">Pull up a chair.</h1>
        <p className="text-center text-[17px] leading-[1.45] text-soft">
          Find the little card on your table and point your camera at it. That&apos;s all it takes.
        </p>

        <div className="mt-2 flex w-[220px] rotate-[-3deg] animate-pop flex-col items-center gap-2.5 rounded-xl bg-paper px-5 pb-4 pt-5 text-bottle shadow-[0_18px_36px_rgba(0,0,0,0.35)]">
          <div className="text-xs font-bold uppercase tracking-[0.14em] text-quiet">Table 12</div>
          <svg aria-hidden="true" width="132" height="132" viewBox="0 0 66 66" fill="currentColor" focusable="false">
            <path d="M3 3h18v18H3zM7 7v10h10V7zM45 3h18v18H45zM49 7v10h10V7zM3 45h18v18H3zM7 49v10h10V49z" fillRule="evenodd" />
            <path d="M10 10h4v4h-4zM52 10h4v4h-4zM10 52h4v4h-4z" />
            <path d="M27 3h4v4h-4zM35 7h4v8h-4zM27 13h4v8h-4zM27 27h8v4h-8zM39 25h4v8h-4zM3 27h8v4H3zM15 31h4v8h-4zM23 35h4v4h-4zM47 27h8v4h-8zM59 31h4v8h-4zM27 43h4v8h-4zM35 39h8v4h-8zM47 43h4v4h-4zM55 47h8v4h-8zM39 51h4v12h-4zM27 59h8v4h-8zM47 55h4v8h-4zM59 59h4v4h-4zM31 19h4v4h-4zM7 35h4v4H7z" />
          </svg>
          <div className="text-center font-hand text-2xl font-bold leading-[1.05] text-hand">Scan me. I&apos;ll take your order.</div>
        </div>

        {ended ? (
          <p role="status" className="mt-1 animate-fade text-center text-[15px] leading-snug text-mustard">
            {ended === 'visit'
              ? 'Your table was closed by the staff. Scan again whenever you sit back down.'
              : 'Your seat has been freed. Scan again whenever you sit back down.'}
          </p>
        ) : null}

        <div className="flex-1" />
        <Button tone="primary" big onClick={() => setHelp(true)}>
          Scan the table code
        </Button>
        <p className="text-center text-sm text-soft">No app to download. No account to make.</p>
        <p className="text-center text-sm text-soft">
          <Link href="/staff/login" className="underline underline-offset-4">
            Staff sign in
          </Link>
        </p>
      </main>

      <Sheet open={help} onClose={() => setHelp(false)} label="How to scan">
        <h2 className="font-heading text-2xl font-normal text-bottle">Point your camera at the card</h2>
        <p className="mt-2 text-[15px] leading-snug text-quiet">
          Open your phone&apos;s camera and hold it over the code on your table. A link pops up: tap it and we&apos;ll bring up your table.
        </p>
        <div className="mt-5">
          <Button tone="bottle" onClick={() => setHelp(false)}>
            Got it
          </Button>
        </div>
      </Sheet>
    </Ground>
  );
}

export default function Home() {
  return (
    <Suspense fallback={null}>
      <Opening />
    </Suspense>
  );
}

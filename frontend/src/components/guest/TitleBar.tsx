'use client';

import { useEffect, useRef } from 'react';
import type { MySession } from '@/lib/guest';

// The table details are the page's title: the business, then "Table 3 · Seat 2", then the guest's name. It stays put
// while the page scrolls, and publishes its own height so whatever pins under it (the menu's chips) sits right below.
export default function TitleBar({ session }: { session: MySession }) {
  const bar = useRef<HTMLElement>(null);
  const mine = session.seats.find((seat) => seat.mine);
  const name = mine?.guestName?.trim() || 'You';

  useEffect(() => {
    const element = bar.current;
    if (!element) return;
    const publish = () => document.documentElement.style.setProperty('--you-h', `${element.offsetHeight}px`);
    publish();
    const watch = new ResizeObserver(publish);
    watch.observe(element);
    return () => {
      watch.disconnect();
      document.documentElement.style.removeProperty('--you-h');
    };
  }, []);

  return (
    <header ref={bar} aria-label="Your table" className="sticky top-0 z-30 border-b border-cream/15 bg-bottle/95 pt-[env(safe-area-inset-top)] backdrop-blur-sm print:hidden">
      <div className="mx-auto w-full max-w-md px-5 py-2 md:max-w-3xl xl:max-w-5xl">
        <div className="break-words text-[12px] font-bold uppercase leading-tight tracking-[0.14em] text-soft">{session.businessName ?? 'Plately'}</div>
        <div className="mt-0.5 flex flex-wrap items-baseline gap-x-2 leading-tight">
          <span className="text-[17px] font-bold text-cream">
            Table {session.displayNumber}
            {mine ? ` · Seat ${mine.position}` : ''}
          </span>
          <span className="break-words text-[15px] text-cream/80">· {name}</span>
        </div>
      </div>
    </header>
  );
}
